package org.jetbrains.research.preprocessing

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.*
import com.github.gumtreediff.matchers.Matchers
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.common.gumtree.wrappers.ActionWrapper
import org.jetbrains.research.common.jgrapht.PatternGraph
import org.jetbrains.research.common.jgrapht.export
import org.jetbrains.research.common.jgrapht.getSuperWeakSubgraphIsomorphismInspector
import org.jetbrains.research.common.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.AsSubgraph
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.HashMap
import kotlin.system.exitProcess


class PreprocessingRunner : ApplicationStarter {

    private val sourceDir: Path = Paths.get("/home/oleg/prog/data/plugin/111")
    private val destDir: Path = Paths.get("/home/oleg/prog/data/plugin/222")
    private val addDescription: Boolean = false

    private var project: Project? = null
    private val logger = Logger.getInstance(this::class.java)

    override fun getCommandName(): String = "preprocessing"

    override fun main(args: Array<out String>) {
        try {
            // Create labels groups and descriptions for each pattern
            val fragmentsByPath = loadFragments(sourceDir)
            val patternByPath = mergeFragments(fragmentsByPath)
            markPatterns(patternByPath, addDescription)

            sourceDir.toFile().listFiles()?.forEach { patternDir ->
                // Import project in order to create PSI
                project = ProjectUtil.openOrImport(patternDir.toPath(), null, true)

                // Prepare actions and extend graphs
                createFragmentToPatternMappings(patternDir)
                extendPatternGraphWithElements(patternDir)
                sortActions(patternDir)
                destDir.resolve(patternDir.name).toFile().mkdirs()

                // Serialize and save edit actions
                val serializedActions = serializeActions(patternDir)
                destDir.resolve(patternDir.name).resolve("actions.json").toFile()
                    .writeText(serializedActions)

                // Save adjusted pattern's graph
                val patternGraph = loadPatternGraph(patternDir)
                patternGraph.export(destDir.resolve(patternDir.name).resolve("graph.dot").toFile())

                // Save labels groups
                destDir.resolve(patternDir.name).resolve("labels_groups.json").toFile()
                    .writeText(labelsGroupsJsonByPath[patternDir.toPath()]!!)

                // Save description
                destDir.resolve(patternDir.name).resolve("description.txt").toFile()
                    .writeText(descriptionByPath[patternDir.toPath()] ?: "No description provided")
            }
        } catch (ex: Exception) {
            logger.error(ex)
        } finally {
            exitProcess(0)
        }
    }

    private val descriptionByPath = HashMap<Path, String>()
    private val labelsGroupsJsonByPath = HashMap<Path, String>()
    private val reprFragmentByPatternPath = HashMap<Path, PatternGraph>()

    private val fragmentToPatternMappingByPattern =
        HashMap<String, HashMap<PatternSpecificVertex, PatternSpecificVertex>>()
    private val psiToPatternMappingByPattern =
        HashMap<String, HashMap<PyElement, PatternSpecificVertex>>()

    private val patternGraphCache = HashMap<String, PatternGraph>()
    private val fragmentGraphCache = HashMap<String, PatternGraph>()
    private val actionsCache = HashMap<String, List<Action>>()
    private val psiCache = HashMap<File, PyElement>()

    private fun loadFragments(inputPatternsStorage: Path): HashMap<Path, ArrayList<PatternGraph>> {
        val fragmentsByDir = HashMap<Path, ArrayList<PatternGraph>>()
        inputPatternsStorage.toFile().walk().forEach { file ->
            if (file.isFile && file.name.startsWith("fragment") && file.extension == "dot") {
                val currentGraph = PatternGraph(file.inputStream())
                val subgraphBefore = AsSubgraph(
                    currentGraph,
                    currentGraph.vertexSet()
                        .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                        .toSet()
                )
                fragmentsByDir.getOrPut(Paths.get(file.parent)) { ArrayList() }.add(subgraphBefore)
            }
        }
        return fragmentsByDir
    }

    private fun mergeFragments(fragmentsMap: HashMap<Path, ArrayList<PatternGraph>>): HashMap<Path, PatternGraph> {
        val patternGraphByPath = HashMap<Path, PatternGraph>()
        for ((path, fragments) in fragmentsMap) {
            val labelsGroupsByVertexId = HashMap<Int, PatternSpecificVertex.LabelsGroup>()
            val repr = fragments.first()
            reprFragmentByPatternPath[path] = repr
            for (graph in fragments.drop(1)) {
                val inspector = getSuperWeakSubgraphIsomorphismInspector(repr, graph)
                if (!inspector.isomorphismExists()) {
                    throw IllegalStateException("Fragments are not isomorphic in the pattern $path")
                } else {
                    val mapping = inspector.mappings.asSequence().first()
                    for (currentVertex in graph.vertexSet()) {
                        if (currentVertex.label?.startsWith("var") == true) {
                            val reprVertex = mapping.getVertexCorrespondence(currentVertex, false)
                            labelsGroupsByVertexId.getOrPut(reprVertex.id) { PatternSpecificVertex.LabelsGroup.getEmpty() }
                                .labels.add(currentVertex.originalLabel!!)
                        }
                    }
                }
            }
            patternGraphByPath[path] = PatternGraph(fragments.first(), labelsGroupsByVertexId)
        }
        return patternGraphByPath
    }

    private fun markPatterns(
        patternDirectedAcyclicGraphByPath: HashMap<Path, PatternGraph>,
        addDescription: Boolean = false
    ) {
        println("Start marking")
        for ((path, graph) in patternDirectedAcyclicGraphByPath) {
            println("-".repeat(70))
            println("Path to current pattern: $path")

            // Add description, which will be shown in the popup (optional)
            if (addDescription) {
                val dotFile = path.toFile()
                    .listFiles { file -> file.extension == "dot" && file.name.startsWith("fragment") }
                    ?.first()
                println("Fragment sample ${dotFile?.name}")
                println(dotFile?.readText())
                println("Your description:")
                val description = readLine() ?: "No description provided"
                println("Final description: $description")
                descriptionByPath[path] = description
            }

            // Choose matching mode
            println("Variable original labels groups:")
            val labelsGroupsByVertexId = HashMap<Int, PatternSpecificVertex.LabelsGroup>()
            for (vertex in graph.vertexSet()) {
                if (vertex.label?.startsWith("var") == true) {
                    println(vertex.dataNodeInfo?.labels)
                    var exit = false
                    while (!exit) {
                        println("Choose, what will be considered as main factor when matching nodes (labels/lcs/nothing):")
                        val ans = BufferedReader(InputStreamReader(System.`in`)).readLine()
                        println("Your answer: $ans")
                        when (ans) {
                            "labels" -> labelsGroupsByVertexId[vertex.id] =
                                PatternSpecificVertex.LabelsGroup(
                                    whatMatters = PatternSpecificVertex.MatchingMode.VALUABLE_ORIGINAL_LABEL,
                                    labels = vertex.dataNodeInfo!!.labels,
                                    longestCommonSuffix = ""
                                ).also { exit = true }
                            "lcs" -> labelsGroupsByVertexId[vertex.id] =
                                PatternSpecificVertex.LabelsGroup(
                                    whatMatters = PatternSpecificVertex.MatchingMode.LONGEST_COMMON_SUFFIX,
                                    labels = vertex.dataNodeInfo!!.labels,
                                    longestCommonSuffix = getLongestCommonSuffix(vertex.dataNodeInfo?.labels)
                                ).also { exit = true }
                            "nothing" -> labelsGroupsByVertexId[vertex.id] =
                                PatternSpecificVertex.LabelsGroup(
                                    whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                                    labels = vertex.dataNodeInfo!!.labels,
                                    longestCommonSuffix = ""
                                ).also { exit = true }
                        }
                    }
                }
            }
            labelsGroupsJsonByPath[path] = Json.encodeToString(labelsGroupsByVertexId)
        }
        println("Finish marking")
    }

    private fun getLongestCommonSuffix(strings: Collection<String?>?): String {
        if (strings == null || strings.isEmpty())
            return ""
        var lcs = strings.first()
        for (string in strings) {
            lcs = lcs?.commonSuffixWith(string ?: "")
        }
        return lcs ?: ""
    }

    private fun loadPsi(file: File): PyFunction? {
        return if (psiCache.containsKey(file)) {
            psiCache[file] as PyFunction
        } else {
            val src: String = file.readText()
            val psi = PsiFileFactory.getInstance(project)
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

    private fun loadPatternGraph(patternDir: File): PatternGraph {
        return if (patternGraphCache.containsKey(patternDir.name)) {
            patternGraphCache[patternDir.name]!!
        } else {
            val changeGraph = reprFragmentByPatternPath[patternDir.toPath()]!!
            val subgraphBefore = AsSubgraph(
                changeGraph,
                changeGraph.vertexSet()
                    .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                    .toSet()
            )
            val labelsGroups = Json.decodeFromString<HashMap<Int, PatternSpecificVertex.LabelsGroup>>(
                labelsGroupsJsonByPath[patternDir.toPath()]!!
            )
            val graph = PatternGraph(subgraphBefore, labelsGroups)
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
}