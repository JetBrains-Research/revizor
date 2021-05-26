package org.jetbrains.research.preprocessing.models

import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Insert
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.jetbrains.python.psi.PyElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.gumtree.wrappers.ActionWrapper
import org.jetbrains.research.common.jgrapht.PatternGraph
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.export
import org.jetbrains.research.common.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.preprocessing.HeuristicActionsComparator
import org.jetbrains.research.preprocessing.getLongestCommonEditActionsSubsequence
import org.jetbrains.research.preprocessing.labelers.HeuristicVerticesMatchingModeLabeler
import org.jetbrains.research.preprocessing.loaders.CachingEditActionsLoader
import org.jetbrains.research.preprocessing.loaders.CachingPsiLoader
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jsoup.Jsoup
import java.io.File
import java.nio.file.Path

class Pattern(directory: File, private val project: Project) {
    val name: String = directory.name
    var description: String = "No description provided"
        private set
    val mainGraph: PatternGraph
    val editActions: EditActions

    val reprFragmentId: Int
    val reprFragment: PatternGraph
    val psiBasedReprFragmentGraph: PatternGraph
    val fragmentById: MutableMap<Int, PatternGraph> = hashMapOf()
    val fullFragmentById: MutableMap<Int, PatternGraph> = hashMapOf()
    val codeChangeSampleById: MutableMap<Int, CodeChangeSample> = hashMapOf()

    private val psiToPsiBasedVertexMapping = hashMapOf<PsiElement, PatternSpecificVertex>()
    private val reprVarVertexToLabelsGroup: MutableMap<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup>
    private val mappings: PsiToMainMappings

    init {
        directory.walk().forEach { file ->
            if (file.isFile && file.name.startsWith("fragment") && file.extension == "dot") {
                val fragmentId = file.nameWithoutExtension.substringAfterLast('-').toInt()
                val fullFragmentGraph = PatternGraph(file.inputStream())
                val fragmentSubgraphBeforeChange = AsSubgraph(
                    fullFragmentGraph,
                    fullFragmentGraph.vertexSet()
                        .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                        .toSet()
                )
                fragmentById[fragmentId] = fragmentSubgraphBeforeChange
                fullFragmentById[fragmentId] = fullFragmentGraph
            }
            if (file.isFile && file.name.startsWith("sample") && file.extension == "html") {
                val fragmentId = file.nameWithoutExtension.substringAfterLast('-').toInt()
                val document = Jsoup.parse(file.readText())
                val codeElements = document.getElementsByClass("code language-python")
                val (codeBefore, codeAfter) = Pair(codeElements[0].text(), codeElements[1].text())
                codeChangeSampleById[fragmentId] = CodeChangeSample(
                    codeBefore = reformatCode(codeBefore),
                    codeAfter = reformatCode(codeAfter),
                )
            }
        }

        // Pick representative fragment by number of corresponding edit actions
        val actionsLoader = CachingEditActionsLoader.getInstance(project)
        val actionsByFragmentId = codeChangeSampleById.mapValues { (_, codeChangeSample) ->
            actionsLoader.loadEditActions(codeChangeSample)
        }
        reprFragmentId = actionsByFragmentId.minByOrNull { (_, actions) -> actions.size }?.key
            ?: throw IllegalStateException("There is no fragments in the pattern to extract edit actions")
        reprFragment = fragmentById[reprFragmentId]!!


        // Create, initialize and mark `mainGraph`
        val labeler = HeuristicVerticesMatchingModeLabeler(
            reprFragment = reprFragment,
            allFragments = fragmentById.values.toList()
        )
        reprVarVertexToLabelsGroup = labeler.markVertices().toMutableMap()
        mainGraph = PatternGraph(
            baseDirectedAcyclicGraph = reprFragment,
            labelsGroupsByVertexId = reprVarVertexToLabelsGroup.mapKeys { it.key.id }
        )


        // Inject PSI elements from representative fragment to the main graph of the pattern
        val reprPsiBefore = CachingPsiLoader.getInstance(project)
            .loadPsiFromSource(src = codeChangeSampleById[reprFragmentId]!!.codeBefore)
        psiBasedReprFragmentGraph = buildPyFlowGraphForMethod(reprPsiBefore)
        for (psiBasedVertex in psiBasedReprFragmentGraph.vertexSet()) {
            psiToPsiBasedVertexMapping[psiBasedVertex.origin!!.psi!!] = psiBasedVertex
        }


        // Prepare mappings from `psiBasedReprFragmentGraph` to `mainGraph`
        val inspector = getWeakSubgraphIsomorphismInspector(psiBasedReprFragmentGraph, mainGraph)
        if (inspector.isomorphismExists()) {
            mappings = PsiToMainMappings(inspector.mappings.asSequence())
        } else {
            throw IllegalStateException("PsiBasedReprFragmentGraph and MainGraph are not isomorphic")
        }


        // Prepare labels groups from `after`-part of full fragments graphs
        val fullFragmentsLabeler = HeuristicVerticesMatchingModeLabeler(
            reprFragment = fullFragmentById.values.first(),
            allFragments = fullFragmentById.values.toList()
        )

        // Extract and sort appropriate edit actions subsequence
        val reprFragmentActions = actionsByFragmentId[reprFragmentId]!!
        val actionsComparator = HeuristicActionsComparator(
            fullFragmentsLabeler.markVertices().values.map { it.labels }
        )
        editActions = actionsByFragmentId.values.fold(
            initial = reprFragmentActions,
            operation = { a1, a2 ->
                getLongestCommonEditActionsSubsequence(a1, a2, actionsComparator::actionsHeuristicallyEquals)
            }
        )
        editActions.sort()


        // Extend `mainGraph` with additional vertices, corresponding to PSI elements involved in edit actions
        val hangerElements: Set<PsiElement> = editActions.collectAdditionalElements()
        extendMainGraphWithHangerElements(hangerElements)
    }

    fun createDescription() {
        println("Current pattern: $name")
        println("Graph nodes' labels: ${this.reprFragment.vertexSet().map { it.label }}")
        var addedDescription = false
        while (!addedDescription) {
            print("Description: ")
            description = readLine()?.also { addedDescription = true } ?: continue
        }
    }

    fun save(directory: Path) {
        directory.toFile().mkdirs()
        directory.resolve("actions.json").toFile().writeText(editActions.getJson())
        mainGraph.export(directory.resolve("graph.dot").toFile())
        directory.resolve("description.txt").toFile().writeText(description)
        directory.resolve("labels_groups.json").toFile()
            .writeText(Json.encodeToString(
                reprVarVertexToLabelsGroup.mapKeys { entry -> entry.key.id }
            ))
    }

    private inner class PsiToMainMappings(
        graphMappings: Sequence<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>
    ) {
        private val psiBasedVertexToMainGraphVertexMappings: List<Map<PatternSpecificVertex, PatternSpecificVertex>>
        private var correctMappings: Set<MutableMap<PatternSpecificVertex, PatternSpecificVertex>>

        init {
            psiBasedVertexToMainGraphVertexMappings = graphMappings.toList().map { jgraphtMapping ->
                val myMapping = hashMapOf<PatternSpecificVertex, PatternSpecificVertex>()
                mainGraph.vertexSet().forEach { mainVertex ->
                    val psiBasedVertex = jgraphtMapping.getVertexCorrespondence(mainVertex, false)
                    myMapping[psiBasedVertex] = mainVertex
                }
                myMapping
            }
            correctMappings = psiBasedVertexToMainGraphVertexMappings.toSet() // initially
        }

        fun filterHasVertex(psiBasedVertex: PatternSpecificVertex?): List<MutableMap<PatternSpecificVertex, PatternSpecificVertex>> {
            return psiBasedVertexToMainGraphVertexMappings
                .filter { it.containsKey(psiBasedVertex) }
                .map { it.toMutableMap() }
        }

        fun updateCorrectMappings(currentCorrectMappings: List<MutableMap<PatternSpecificVertex, PatternSpecificVertex>>) {
            correctMappings = correctMappings.intersect(currentCorrectMappings)
        }

        fun getRepresentative(): MutableMap<PatternSpecificVertex, PatternSpecificVertex> = correctMappings.first()
    }

    /**
     * Collect PSI elements which are involved in edit actions but are not contained in the pattern's graph
     */
    private fun EditActions.collectAdditionalElements(): Set<PsiElement> {
        val insertedElements = hashSetOf<PyElement>()
        val hangerElements = hashSetOf<PyElement>()
        for (action in this) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            if (action is Update || action is Delete || action is Move) {
                val psiBasedVertex = psiToPsiBasedVertexMapping[element]
                val mappingsHavingVertex = mappings.filterHasVertex(psiBasedVertex)
                if (mappingsHavingVertex.isEmpty()) {
                    hangerElements.add(element)
                } else {
                    mappings.updateCorrectMappings(mappingsHavingVertex)
                }
            }
            if (action is Insert) {
                val newElement = (action.node as PyPsiGumTree).rootElement ?: continue
                insertedElements.add(newElement)
            }
            if (action is Insert || action is Move) {
                val parent = (action as? Insert)?.parent ?: (action as? Move)?.parent
                val parentElement = (parent as PyPsiGumTree).rootElement!!
                if (insertedElements.contains(parentElement))
                    continue

                val parentPsiBasedVertex = psiToPsiBasedVertexMapping[parentElement]
                val mappingsHavingParentVertex = mappings.filterHasVertex(parentPsiBasedVertex)
                if (mappingsHavingParentVertex.isEmpty()) {
                    hangerElements.add(parentElement)
                } else {
                    mappings.updateCorrectMappings(mappingsHavingParentVertex)
                }
            }
        }
        return hangerElements
    }

    /**
     * Add vertices (containing given PyElements) to pattern graph and connect them to all its neighbours,
     * because `VF2SubgraphIsomorphismMatcher` will match only among induced subgraphs
     */
    private fun extendMainGraphWithHangerElements(hangerElements: Set<PsiElement>) {
        val mapping = mappings.getRepresentative()
        for (element in hangerElements) {
            val psiBasedVertex = psiToPsiBasedVertexMapping[element] ?: continue
            val newVertex = psiBasedVertex.copy()
            if (newVertex.label?.startsWith("var") == true) {
                newVertex.dataNodeInfo = PatternSpecificVertex.LabelsGroup.getUniversal()
                reprVarVertexToLabelsGroup[newVertex] = newVertex.dataNodeInfo!!
            }
            newVertex.metadata = "hanger"
            mainGraph.addVertex(newVertex)
            mapping[psiBasedVertex] = newVertex
        }
        for (element in hangerElements) {
            val psiBasedVertex = psiToPsiBasedVertexMapping[element] ?: continue
            val newVertex = mapping[psiBasedVertex]!!
            for (incomingEdge in psiBasedReprFragmentGraph.incomingEdgesOf(psiBasedVertex)) {
                val fragmentEdgeSource = psiBasedReprFragmentGraph.getEdgeSource(incomingEdge)
                val patternEdgeSource = mapping[fragmentEdgeSource] ?: continue
                mainGraph.addEdge(patternEdgeSource, newVertex, incomingEdge)
            }
            for (outgoingEdge in psiBasedReprFragmentGraph.outgoingEdgesOf(psiBasedVertex)) {
                val fragmentEdgeTarget = psiBasedReprFragmentGraph.getEdgeTarget(outgoingEdge)
                val patternEdgeTarget = mapping[fragmentEdgeTarget] ?: continue
                mainGraph.addEdge(newVertex, patternEdgeTarget, outgoingEdge)
            }
        }
    }

    private fun EditActions.getJson(): String {
        val actionsWrappers = arrayListOf<ActionWrapper>()
        val mapping = mappings.getRepresentative()
        for (action in this) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            when (action) {
                is Update -> {
                    (action.node as PyPsiGumTree).rootVertex =
                        mapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.UpdateActionWrapper(action))
                }
                is Delete -> {
                    (action.node as PyPsiGumTree).rootVertex =
                        mapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.DeleteActionWrapper(action))
                }
                is Insert -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex =
                        mapping[psiToPsiBasedVertexMapping[parentElement]]
                    actionsWrappers.add(ActionWrapper.InsertActionWrapper(action))
                }
                is Move -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex =
                        mapping[psiToPsiBasedVertexMapping[parentElement]]
                    (action.node as PyPsiGumTree).rootVertex =
                        mapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.MoveActionWrapper(action))
                }
            }
        }
        return Json.encodeToString(actionsWrappers)
    }

    private fun reformatCode(code: String): String {
        val psiLoader = CachingPsiLoader.getInstance(project)
        val formatter = CodeStyleManager.getInstance(project)
        return WriteCommandAction.runWriteCommandAction<PsiElement>(project) {
            formatter.reformat(psiLoader.loadPsiFromSource(code))
        }.text
    }
}