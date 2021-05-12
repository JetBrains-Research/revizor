package org.jetbrains.research.preprocessing.models

import com.github.gumtreediff.actions.model.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElement
import com.jetbrains.rd.util.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.gumtree.wrappers.ActionWrapper
import org.jetbrains.research.common.jgrapht.PatternGraph
import org.jetbrains.research.common.jgrapht.export
import org.jetbrains.research.common.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.preprocessing.getLongestCommonEditActionsSubsequence
import org.jetbrains.research.preprocessing.labelers.HeuristicVerticesMatchingModeLabeler
import org.jetbrains.research.preprocessing.loaders.CachingEditActionsLoader
import org.jetbrains.research.preprocessing.loaders.CachingPsiLoader
import org.jetbrains.research.preprocessing.sortEditActions
import org.jgrapht.graph.AsSubgraph
import org.jsoup.Jsoup
import java.nio.file.Path

class Pattern(private val directory: Path, private val project: Project) {
    val name: String = directory.toFile().name
    var description: String = "No description provided"
        private set
    val mainGraph: PatternGraph
    val editActions: List<Action>

    val reprFragmentId: Int
    val reprFragment: PatternGraph
    val psiBasedReprFragmentGraph: PatternGraph
    val fragmentById: MutableMap<Int, PatternGraph> = hashMapOf()
    val codeChangeSampleById: MutableMap<Int, CodeChangeSample> = hashMapOf()

    private val psiToPsiBasedVertexMapping = hashMapOf<PsiElement, PatternSpecificVertex>()
    private val psiBasedVertexToMainGraphVertexMapping = hashMapOf<PatternSpecificVertex, PatternSpecificVertex>()
    private val variableVertexToLabelsGroup: Map<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup>

    init {
        directory.toFile().walk().forEach { file ->
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
            }
            if (file.isFile && file.name.startsWith("sample") && file.extension == "html") {
                val fragmentId = file.nameWithoutExtension.substringAfterLast('-').toInt()
                val document = Jsoup.parse(file.readText())
                val codeElements = document.getElementsByClass("code language-python")
                codeChangeSampleById[fragmentId] = CodeChangeSample(
                    codeBefore = codeElements[0].text(),
                    codeAfter = codeElements[1].text(),
                )
            }
        }
        reprFragmentId = fragmentById.first().key
        reprFragment = fragmentById.first().value

        // Create, initialize and mark `mainGraph`
        val labeler = HeuristicVerticesMatchingModeLabeler(
            reprFragment = reprFragment,
            allFragments = fragmentById.values.toList()
        )
        variableVertexToLabelsGroup = labeler.markVertices()
        mainGraph = PatternGraph(
            baseDirectedAcyclicGraph = reprFragment,
            labelsGroupsByVertexId = variableVertexToLabelsGroup.mapKeys { it.key.id })

        // Inject PSI elements from representative `codeChangeSample` to the `mainGraph`
        val reprPsiBefore = CachingPsiLoader.getInstance(project)
            .loadPsiFromSource(src = codeChangeSampleById[reprFragmentId]!!.codeBefore)
        psiBasedReprFragmentGraph = buildPyFlowGraphForMethod(reprPsiBefore)
        for (psiBasedVertex in psiBasedReprFragmentGraph.vertexSet()) {
            psiToPsiBasedVertexMapping[psiBasedVertex.origin!!.psi!!] = psiBasedVertex
        }
        injectPsiElementsToMainGraph()

        // Extract and sort appropriate edit actions subsequence
        val actionsByFragmentId = codeChangeSampleById.mapValues { (_, codeChangeSample) ->
            CachingEditActionsLoader.getInstance(project).loadEditActions(codeChangeSample)
        }
        var reprActions = actionsByFragmentId[reprFragmentId]!!
        actionsByFragmentId.forEach { (_, actions) ->
            reprActions = getLongestCommonEditActionsSubsequence(reprActions, actions)
        }
        editActions = reprActions
        sortEditActions(editActions)

        // Extend `mainGraph` with additional vertices, corresponding to PSI elements involved in edit actions
        val hangerElements: Set<PsiElement> = collectAdditionalElementsFromActions()
        extendMainGraphWithHangerElements(hangerElements)
    }

    private fun injectPsiElementsToMainGraph() {
        val inspector = getWeakSubgraphIsomorphismInspector(psiBasedReprFragmentGraph, mainGraph)
        var foundCorrectMapping = false

        if (inspector.isomorphismExists()) {
            for (mapping in inspector.mappings.asSequence()) {
                var isCorrectMapping = true
                for (mainVertex in mainGraph.vertexSet()) {
                    val psiBasedVertex = mapping.getVertexCorrespondence(mainVertex, false)
                    psiBasedVertexToMainGraphVertexMapping[psiBasedVertex] = mainVertex

                    if (mainVertex.originalLabel?.toLowerCase() != psiBasedVertex.originalLabel?.toLowerCase()) {
                        isCorrectMapping = false
                        break
                    }
                }
                if (isCorrectMapping) {
                    foundCorrectMapping = true
                    break
                }
            }
        }
        if (!foundCorrectMapping) {
            throw IllegalStateException("Correct mapping was not found")
        }
    }

    /**
     * Collect PSI elements which are involved in edit actions but are not contained in the pattern's graph
     */
    private fun collectAdditionalElementsFromActions(): Set<PsiElement> {
        val insertedElements = hashSetOf<PyElement>()
        val hangerElements = hashSetOf<PyElement>()
        for (action in editActions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            if (action is Update || action is Delete || action is Move) {
                if (psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[element]] == null)
                    hangerElements.add(element)
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
                hangerElements.add(parentElement)
                if (psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[parentElement]] == null)
                    hangerElements.add(parentElement)
            }
        }
        return hangerElements
    }

    /**
     * Add vertices (containing given PyElements) to pattern graph and connect them to all its neighbours,
     * because `VF2SubgraphIsomorphismMatcher` will match only among induced subgraphs
     */
    private fun extendMainGraphWithHangerElements(hangerElements: Set<PsiElement>) {
        for (element in hangerElements) {
            val psiBasedVertex = psiToPsiBasedVertexMapping[element] ?: continue
            val newVertex = psiBasedVertex.copy()
            if (newVertex.label?.startsWith("var") == true) {
                newVertex.dataNodeInfo = PatternSpecificVertex.LabelsGroup(
                    whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                    labels = hashSetOf(),
                    longestCommonSuffix = ""
                )
            }
            newVertex.metadata = "hanger"
            mainGraph.addVertex(newVertex)
            psiBasedVertexToMainGraphVertexMapping[psiBasedVertex] = newVertex
        }
        for (element in hangerElements) {
            val psiBasedVertex = psiToPsiBasedVertexMapping[element] ?: continue
            val newVertex = psiBasedVertexToMainGraphVertexMapping[psiBasedVertex]!!
            for (incomingEdge in psiBasedReprFragmentGraph.incomingEdgesOf(psiBasedVertex)) {
                val fragmentEdgeSource = psiBasedReprFragmentGraph.getEdgeSource(incomingEdge)
                val patternEdgeSource = psiBasedVertexToMainGraphVertexMapping[fragmentEdgeSource] ?: continue
                mainGraph.addEdge(patternEdgeSource, newVertex, incomingEdge)
            }
            for (outgoingEdge in psiBasedReprFragmentGraph.outgoingEdgesOf(psiBasedVertex)) {
                val fragmentEdgeTarget = psiBasedReprFragmentGraph.getEdgeTarget(outgoingEdge)
                val patternEdgeTarget = psiBasedVertexToMainGraphVertexMapping[fragmentEdgeTarget] ?: continue
                mainGraph.addEdge(newVertex, patternEdgeTarget, outgoingEdge)
            }
        }
    }

    fun save(directory: Path) {
        val serializedActions = getEditActionsJson()
        directory.resolve("actions.json").toFile().writeText(serializedActions)
        mainGraph.export(directory.resolve("graph.dot").toFile())
        directory.resolve("description.txt").toFile().writeText(description)
        directory.resolve("labels_groups.json").toFile()
            .writeText(Json.encodeToString(variableVertexToLabelsGroup.values.toList())) // FIXME: dict instead of list
    }

    private fun getEditActionsJson(): String {
        val actionsWrappers = arrayListOf<ActionWrapper>()
        for (action in editActions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            when (action) {
                is Update -> {
                    (action.node as PyPsiGumTree).rootVertex =
                        psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.UpdateActionWrapper(action))
                }
                is Delete -> {
                    (action.node as PyPsiGumTree).rootVertex =
                        psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.DeleteActionWrapper(action))
                }
                is Insert -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex =
                        psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[parentElement]]
                    actionsWrappers.add(ActionWrapper.InsertActionWrapper(action))
                }
                is Move -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex =
                        psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[parentElement]]
                    (action.node as PyPsiGumTree).rootVertex =
                        psiBasedVertexToMainGraphVertexMapping[psiToPsiBasedVertexMapping[element]]
                    actionsWrappers.add(ActionWrapper.MoveActionWrapper(action))
                }
            }
        }
        return Json.encodeToString(actionsWrappers)
    }
}