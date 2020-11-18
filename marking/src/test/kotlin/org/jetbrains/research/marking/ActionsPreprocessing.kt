package org.jetbrains.research.marking

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.*
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.plugin.gumtree.wrappers.ActionWrapper
import org.jetbrains.research.plugin.jgrapht.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.jgrapht.export
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.AsSubgraph
import java.io.File
import java.util.*
import kotlin.collections.HashMap


class ActionsPreprocessing : BasePlatformTestCase() {

    companion object {
        const val PATTERNS_SRC = "/home/oleg/prog/data/plugin/jar_patterns/old_patterns"
        const val PATTERNS_DEST = "/home/oleg/prog/data/plugin/jar_patterns/new_patterns"
    }

    private val logger = Logger.getInstance(this::class.java)

    private val fragmentToPatternMappingByPattern =
        HashMap<String, HashMap<PatternSpecificVertex, PatternSpecificVertex>>()
    private val psiToPatternMappingByPattern =
        HashMap<String, HashMap<PyElement, PatternSpecificVertex>>()

    private val patternGraphCache = HashMap<String, PatternDirectedAcyclicGraph>()
    private val fragmentGraphCache = HashMap<String, PatternDirectedAcyclicGraph>()
    private val actionsCache = HashMap<String, List<Action>>()
    private val psiCache = HashMap<File, PyElement>()

    private fun loadPsi(file: File): PyFunction? {
        return if (psiCache.containsKey(file)) {
            psiCache[file] as PyFunction
        } else {
            val src: String = file.readText()
            val psi = PsiFileFactory.getInstance(myFixture.project)
                .createFileFromText(PythonLanguage.getInstance(), src)
                .children.first() as PyFunction
            psiCache[file] = psi
            psi
        }
    }

    private fun loadEditActions(patternDir: File): List<Action> {
        return if (actionsCache.containsKey(patternDir.name)) {
            actionsCache[patternDir.name]!!
        } else {
            var actions: List<Action> = arrayListOf()
            try {
                val pyFunctionBefore = loadPsi(patternDir.toPath().resolve("before.py").toFile())!!
                val pyFunctionAfter = loadPsi(patternDir.toPath().resolve("after.py").toFile())!!
                val srcGumtree = PyPsiGumTreeGenerator().generate(pyFunctionBefore).root
                val dstGumtree = PyPsiGumTreeGenerator().generate(pyFunctionAfter).root
                val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree).also { it.match() }
                val generator = ActionGenerator(srcGumtree, dstGumtree, matcher.mappings)
                actions = generator.generate()
            } catch (ex: Exception) {
                logger.error(ex)
            }
            actionsCache[patternDir.name] = actions
            actions
        }
    }

    private fun loadPatternGraph(patternDir: File): PatternDirectedAcyclicGraph {
        return if (patternGraphCache.containsKey(patternDir.name)) {
            patternGraphCache[patternDir.name]!!
        } else {
            val dotFiles = patternDir.listFiles { _, name -> name.endsWith(".dot") }!!
            val inputDotStream = dotFiles[0].inputStream()
            val changeGraph = PatternDirectedAcyclicGraph(inputDotStream)
            val subgraphBefore = AsSubgraph(
                changeGraph,
                changeGraph.vertexSet()
                    .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                    .toSet()
            )
            val labelsGroupsSrc = patternDir.toPath().resolve("labels_groups.json").toFile().readText()
            val labelsGroups = Json.decodeFromString<HashMap<Int, PatternSpecificVertex.LabelsGroup>>(labelsGroupsSrc)
            val graph = PatternDirectedAcyclicGraph(subgraphBefore, labelsGroups)
            patternGraphCache[patternDir.name] = graph
            graph
        }
    }

    /**
     * Create mapping from fragment's PSI elements to pattern's graph vertices
     * Also create mapping from fragment's graph vertices to pattern's graph vertices
     * Also save fragment's graph to cache
     */
    private fun createFragmentToPatternMappings(patternDir: File) {
        val fragmentPsi = loadPsi(patternDir.toPath().resolve("before.py").toFile())!!
        val patternGraph = loadPatternGraph(patternDir)
        val fragmentGraph = buildPyFlowGraphForMethod(fragmentPsi, builder = "kotlin")
        fragmentGraphCache[patternDir.name] = fragmentGraph
        val fragmentToPatternMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
        val psiToPatternMapping = HashMap<PyElement, PatternSpecificVertex>()
        val inspector = getWeakSubgraphIsomorphismInspector(fragmentGraph, patternGraph)
        if (inspector.isomorphismExists()) {
            val mapping = inspector.mappings.asSequence().first()
            for (patternVertex in patternGraph.vertexSet()) {
                val fragmentVertex = mapping.getVertexCorrespondence(patternVertex, false)
                fragmentToPatternMapping[fragmentVertex] = patternVertex
                psiToPatternMapping[fragmentVertex.origin!!.psi!!] = patternVertex
            }
            fragmentToPatternMappingByPattern[patternDir.name] = fragmentToPatternMapping
            psiToPatternMappingByPattern[patternDir.name] = psiToPatternMapping
        } else {
            logger.error("Pattern's graph does not match to pattern's code fragment (before.py)")
        }
    }

    /**
     * Collect PSI elements which are involved in edit actions but are not contained in the pattern's graph
     */
    private fun collectAdditionalElementsFromActions(patternDir: File): Set<PyElement> {
        val psiToPatternVertex = psiToPatternMappingByPattern[patternDir.name]!!
        val actions = loadEditActions(patternDir)
        val insertedElements = hashSetOf<PyElement>()
        val hangerElements = hashSetOf<PyElement>()
        for (action in actions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            if (action is Update || action is Delete || action is Move) {
                if (!psiToPatternVertex.containsKey(element))
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
                if (!psiToPatternVertex.containsKey(parentElement))
                    hangerElements.add(parentElement)
            }
        }
        return hangerElements
    }

    /**
     * Add vertices (containing given PyElements) to pattern graph and connect them to all its neighbours,
     * because `VF2SubgraphIsomorphismMatcher` will match only among induced subgraphs
     */
    private fun extendPatternGraphWithElements(patternDir: File) {
        val patternGraph = loadPatternGraph(patternDir)
        val fragmentGraph = fragmentGraphCache[patternDir.name]!!
        val fragmentToPatternMapping = fragmentToPatternMappingByPattern[patternDir.name]!!
        val hangerElements = collectAdditionalElementsFromActions(patternDir)
        for (element in hangerElements) {
            val originalVertex = fragmentGraph.vertexSet().find { it.origin?.psi == element }
            if (originalVertex == null
                || fragmentToPatternMapping.containsKey(originalVertex)
                && patternGraph.containsVertex(fragmentToPatternMapping[originalVertex])
            ) {
                continue
            }
            val newVertex = originalVertex.copy()
            psiToPatternMappingByPattern[patternDir.name]?.put(element, newVertex)
            fragmentToPatternMapping[originalVertex] = newVertex
            if (newVertex.label?.startsWith("var") == true) {
                newVertex.dataNodeInfo = PatternSpecificVertex.LabelsGroup(
                    whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                    labels = hashSetOf(),
                    longestCommonSuffix = ""
                )
            }
            newVertex.metadata = "hanger"
            patternGraph.addVertex(newVertex)
            for (incomingEdge in fragmentGraph.incomingEdgesOf(originalVertex)) {
                val fragmentEdgeSource = fragmentGraph.getEdgeSource(incomingEdge)
                val patternEdgeSource = fragmentToPatternMapping[fragmentEdgeSource] ?: continue
                patternGraph.addEdge(patternEdgeSource, newVertex, incomingEdge)
            }
            for (outgoingEdge in fragmentGraph.outgoingEdgesOf(originalVertex)) {
                val fragmentEdgeTarget = fragmentGraph.getEdgeTarget(outgoingEdge)
                val patternEdgeTarget = fragmentToPatternMapping[fragmentEdgeTarget] ?: continue
                patternGraph.addEdge(newVertex, patternEdgeTarget, outgoingEdge)
            }
        }
    }

    /**
     * Swap `Update` and `Move` actions which keeps the node with the same type and label,
     * since it could produce bugs with updating already moved nodes
     */
    private fun sortActions(patternDir: File) {
        val actions = loadEditActions(patternDir)
        val updates = arrayListOf<Pair<Int, Update>>()
        for ((i, action) in actions.withIndex()) {
            if (action is Update) {
                updates.add(Pair(i, action))
                continue
            }
            if (action is Move) {
                val item = updates.find { it.second.node.hasSameTypeAndLabel(action.node) } ?: continue
                Collections.swap(actions, i, item.first)
            }
        }
    }

    /**
     * Add the corresponding `PatternSpecificVertex` node to each action, and serialize it
     */
    private fun serializeActions(patternDir: File): String {
        val psiToPatternVertex = psiToPatternMappingByPattern[patternDir.name]!!
        val actions = loadEditActions(patternDir)
        val actionsWrappers = arrayListOf<ActionWrapper>()
        for (action in actions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            when (action) {
                is Update -> {
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    actionsWrappers.add(ActionWrapper.UpdateActionWrapper(action))
                }
                is Delete -> {
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    actionsWrappers.add(ActionWrapper.DeleteActionWrapper(action))
                }
                is Insert -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                    actionsWrappers.add(ActionWrapper.InsertActionWrapper(action))
                }
                is Move -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    actionsWrappers.add(ActionWrapper.MoveActionWrapper(action))
                }
            }
        }
        return Json.encodeToString(actionsWrappers)
    }

    fun test() {
        File(PATTERNS_SRC).listFiles()?.forEach { patternDir ->
            createFragmentToPatternMappings(patternDir)
            extendPatternGraphWithElements(patternDir)
            sortActions(patternDir)
            val dest = File(PATTERNS_DEST).resolve(patternDir.name)
            patternDir.copyRecursively(dest, overwrite = true)
            dest.listFiles()!!.filter { it.extension == "dot" }.forEach { it.delete() }
            val serializedActions = serializeActions(patternDir)
            dest.resolve("actions.json").writeText(serializedActions)
            val patternGraph = loadPatternGraph(patternDir)
            patternGraph.export(dest.resolve("graph.dot"))
        }
    }
}