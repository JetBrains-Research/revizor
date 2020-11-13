package org.jetbrains.research.plugin

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.*
import com.github.gumtreediff.matchers.Matchers
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.modifying.PyPsiGumTree
import org.jetbrains.research.plugin.modifying.PyPsiGumTreeGenerator
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

typealias PatternGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>

/**
 * A singleton class for storing graph patterns.
 *
 * This class provides functionality for retrieving patterns from JAR file,
 * extracting descriptions and common variables' labels for each pattern. Also,
 * it has `getIsomorphicPatterns` method which is used for patterns localization.
 */
object PatternsStorage {
    private val patternDescriptionById = HashMap<String, String>()

    private val patternGraphById = HashMap<String, PatternGraph>()
    private val fragmentGraphById = HashMap<String, PatternGraph>()
    private val fragmentGraphToPatternGraphMappingById =
            HashMap<String, HashMap<PatternSpecificVertex, PatternSpecificVertex>>()
    private val fragmentPsiToPatternGraphMappingById =
            HashMap<String, HashMap<PyElement, PatternSpecificVertex>>()

    private val patternEditActionsById = HashMap<String, List<Action>>()
    private val patternPsiBeforeById = HashMap<String, PyFunction?>()
    private val patternPsiAfterById = HashMap<String, PyFunction?>()

    private val logger = Logger.getInstance(this::class.java)
    lateinit var project: Project

    fun init(project: Project) {
        this.project = project
        var jar: JarFile? = null
        try {
            val resourceFile = File(this::class.java.getResource("").path)
            val jarFilePath = Paths.get(URL(resourceFile.path).toURI()).toString().substringBeforeLast("!")
            jar = JarFile(jarFilePath)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val je = entries.nextElement()
                val jarEntryPathParts = je.name.split("/")
                val patternId = jarEntryPathParts.getOrNull(1)
                if (jarEntryPathParts.size == 3
                        && jarEntryPathParts.first() == "patterns"
                        && patternId?.toIntOrNull() != null
                        && jarEntryPathParts.last().endsWith(".dot")
                ) {
                    val dotSrcStream = this::class.java.getResourceAsStream("/${je.name}")
                    val fullGraph = createPatternSpecificGraph(dotSrcStream)
                    val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                            fullGraph,
                            fullGraph.vertexSet()
                                    .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                                    .toSet()
                    )
                    val variableLabelsGroups = loadVariableLabelsGroups(patternId) ?: arrayListOf()
                    val patternGraph: PatternGraph = createPatternSpecificGraph(subgraphBefore, variableLabelsGroups)
                    patternGraphById[patternId] = patternGraph
                    loadFragment(patternId, patternGraph)
                    finalizePatternGraphUsingEditActions(patternId)
                }
            }
        } catch (ex: Exception) {
            logger.warn("Failed to load patterns resources from JAR file")
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String): PatternGraph? =
            patternGraphById[patternId]

    fun getPatternDescriptionById(patternId: String): String =
            patternDescriptionById.getOrPut(patternId) {
                loadDescription(patternId) ?: "Unnamed pattern: $patternId"
            }

    fun getPatternEditActionsById(patternId: String): List<Action> =
            patternEditActionsById.getOrPut(patternId) { loadEditActionsFromPattern(patternId) }

    fun getPatternPsiBeforeById(patternId: String): PyFunction? =
            patternPsiBeforeById.getOrPut(patternId) { loadPsiMethodFromPattern(patternId, "before.py") }

    fun getPatternPsiAfterById(patternId: String): PyFunction? =
            patternPsiAfterById.getOrPut(patternId) { loadPsiMethodFromPattern(patternId, "after.py") }

    fun getIsomorphicPatterns(targetGraph: PatternGraph)
            : HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>> {
        val suitablePatterns =
                HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()
        for ((patternId, patternGraph) in patternGraphById) {
            val inspector = getWeakSubgraphIsomorphismInspector(targetGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                for (mapping in inspector.mappings) {
                    suitablePatterns.getOrPut(patternId) { arrayListOf() }.add(mapping)
                }
            }
        }
        return suitablePatterns
    }

    private fun loadFragment(
            patternId: String,
            patternGraph: PatternGraph
    ) {
        try {
            val fragmentPsi = getPatternPsiBeforeById(patternId)!!
            val fragmentGraph = buildPyFlowGraphForMethod(fragmentPsi, builder = "kotlin")
            fragmentGraphById[patternId] = fragmentGraph
            val fragmentVertexToPatternVertexMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
            val psiToPatternVertexMapping = HashMap<PyElement, PatternSpecificVertex>()
            val inspector = getWeakSubgraphIsomorphismInspector(fragmentGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().first()
                for (patternVertex in patternGraph.vertexSet()) {
                    val fragmentVertex = mapping.getVertexCorrespondence(patternVertex, false)
                    fragmentVertexToPatternVertexMapping[fragmentVertex] = patternVertex
                    psiToPatternVertexMapping[fragmentVertex.origin!!.psi!!] = patternVertex
                }
                fragmentGraphToPatternGraphMappingById[patternId] = fragmentVertexToPatternVertexMapping
                fragmentPsiToPatternGraphMappingById[patternId] = psiToPatternVertexMapping
            } else {
                logger.error("Pattern's graph does not match to pattern's code fragment (before.py)")
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }

    private fun extendPatternGraphWithElements(patternId: String, elements: Set<PyElement>, areHangers: Boolean = false) {
        try {
            // Load pattern graph, fragment graph and its psi
            val patternGraph = getPatternById(patternId)!!
            val fragmentGraph = fragmentGraphById[patternId]!!
            val fragmentToPatternMapping = fragmentGraphToPatternGraphMappingById[patternId]!!

            // Add corresponding vertices to pattern graph and connect them to all its neighbours,
            // because VF2SubgraphIsomorphismMatcher will match only among induced subgraphs
            for (element in elements) {
                val originalVertex = fragmentGraph.vertexSet().find { it.origin?.psi == element }
                if (originalVertex == null
                        || fragmentToPatternMapping.containsKey(originalVertex)
                        && patternGraph.containsVertex(fragmentToPatternMapping[originalVertex])
                ) {
                    continue
                }
                val newVertex = originalVertex.copy()
                fragmentPsiToPatternGraphMappingById[patternId]?.put(element, newVertex)
                fragmentToPatternMapping[originalVertex] = newVertex
                if (newVertex.label?.startsWith("var") == true) {
                    newVertex.dataNodeInfo = PatternSpecificVertex.LabelsGroup(
                        whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                        labels = hashSetOf(),
                        longestCommonSuffix = ""
                    )
                }
                if (areHangers) {
                    newVertex.metadata = "hanger"
                }
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
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }

    private fun finalizePatternGraphUsingEditActions(patternId: String) {
        try {
            val actions = getPatternEditActionsById(patternId)
            val psiToPatternVertex = fragmentPsiToPatternGraphMappingById[patternId]!!

            // Collect elements which are involved in edit actions but are not contained in pattern graph
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

            // Add them to the pattern graph
            extendPatternGraphWithElements(patternId, hangerElements, areHangers = true)

            // Propagate links to pattern vertices in each edit action
            for (action in actions) {
                val element = (action.node as PyPsiGumTree).rootElement!!
                when (action) {
                    is Update, is Delete -> {
                        (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    }
                    is Insert -> {
                        val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                        (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                    }
                    is Move -> {
                        val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                        (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                        (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    }
                }
            }

            // Swap connected Updates and Moves
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
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }

    private fun loadEditActionsFromPattern(patternId: String): List<Action> {
        var actions: List<Action> = arrayListOf()
        try {
            val pyFunctionBefore = getPatternPsiBeforeById(patternId)!!
            val pyFunctionAfter = getPatternPsiAfterById(patternId)!!
            val srcGumtree = PyPsiGumTreeGenerator().generate(pyFunctionBefore).root
            val dstGumtree = PyPsiGumTreeGenerator().generate(pyFunctionAfter).root
            val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree).also { it.match() }
            val mappings = matcher.mappings
            val generator = ActionGenerator(srcGumtree, dstGumtree, mappings)
            actions = generator.generate()
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return actions
    }

    private fun loadPsiMethodFromPattern(patternId: String, fileName: String): PyFunction? {
        return try {
            val pathToSampleBefore = "/patterns/$patternId/$fileName"
            val sampleSrc: String = this::class.java.getResource(pathToSampleBefore).readText()
            PsiFileFactory.getInstance(project)
                    .createFileFromText(PythonLanguage.getInstance(), sampleSrc)
                    .children.first() as PyFunction
        } catch (ex: Exception) {
            logger.error(ex)
            null
        }
    }

    private fun loadDescription(patternId: String): String? {
        return try {
            this::class.java.getResource("/patterns/$patternId/description.txt").readText()
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadVariableLabelsGroups(patternId: String): ArrayList<PatternSpecificVertex.LabelsGroup>? {
        return try {
            val filePath = "/patterns/$patternId/possible_variable_labels.json"
            val fileContent = this::class.java.getResource(filePath).readText()
            val type = object : TypeToken<ArrayList<PatternSpecificVertex.LabelsGroup>>() {}.type
            val json = Gson().fromJson<ArrayList<PatternSpecificVertex.LabelsGroup>>(fileContent, type)
            val labelsGroups = ArrayList<PatternSpecificVertex.LabelsGroup>()
            json.forEach {
                labelsGroups.add(
                        PatternSpecificVertex.LabelsGroup(
                                whatMatters = it.whatMatters,
                                labels = it.labels.toHashSet(),
                                longestCommonSuffix = it.longestCommonSuffix
                        )
                )
            }
            labelsGroups
        } catch (ex: Exception) {
            null
        }
    }
}