package org.jetbrains.research.plugin

import com.github.gumtreediff.actions.model.Action
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.gumtree.wrappers.ActionWrapper
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.loadPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.Graph
import org.jgrapht.GraphMapping
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile
import kotlin.collections.HashMap

typealias PatternGraph = Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>
typealias PatternDirectedAcyclicGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>

/**
 * A singleton class for storing graph patterns.
 *
 * This class provides functionality for retrieving patterns from JAR file,
 * extracting descriptions and common variables' labels for each pattern. Also,
 * it has `getIsomorphicPatterns` method which is used for patterns localization.
 */
object PatternsStorage {
    private val patternDescriptionById = HashMap<String, String>()

    private val patternGraphById = HashMap<String, PatternDirectedAcyclicGraph>()
    private val fragmentGraphById = HashMap<String, PatternDirectedAcyclicGraph>()
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
                val entryPath = entries.nextElement().name
                if (entryPath.matches("^patterns/[-_.A-Za-z0-9]+/graph.dot$".toRegex())) {
                    val patternId = entryPath.split("/")[1]
                    val dotSrcStream = this::class.java.getResourceAsStream("/$entryPath")
                    val initialGraph = loadPatternSpecificGraph(dotSrcStream)
                    val labelsGroups = loadLabelsGroups(patternId)
                    val patternDirectedAcyclicGraph: PatternDirectedAcyclicGraph =
                        createPatternSpecificGraph(initialGraph, labelsGroups)
                    patternGraphById[patternId] = patternDirectedAcyclicGraph
                }
            }
        } catch (ex: Exception) {
            logger.warn("Failed to load patterns resources from JAR file")
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String): PatternDirectedAcyclicGraph? = patternGraphById[patternId]

    fun getPatternDescriptionById(patternId: String): String =
        patternDescriptionById.getOrPut(patternId) { loadDescription(patternId) ?: "Unnamed pattern: $patternId" }

    fun getPatternEditActionsById(patternId: String): List<Action> =
        patternEditActionsById.getOrPut(patternId) { loadEditActionsFromPattern(patternId) }

    fun getIsomorphicPatterns(targetDirectedAcyclicGraph: PatternDirectedAcyclicGraph)
            : HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>> {
        val suitablePatterns =
            HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()
        for ((patternId, patternGraph) in patternGraphById) {
            val inspector = getWeakSubgraphIsomorphismInspector(targetDirectedAcyclicGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                for (mapping in inspector.mappings) {
                    suitablePatterns.getOrPut(patternId) { arrayListOf() }.add(mapping)
                }
            }
        }
        return suitablePatterns
    }

    private fun loadEditActionsFromPattern(patternId: String): List<Action> {
        val actions = arrayListOf<Action>()
        try {
            val filePath = "/patterns/$patternId/actions.json"
            val fileContent = this::class.java.getResource(filePath).readText()
            val actionsWrappers = Json.decodeFromString<List<ActionWrapper>>(fileContent)
            val patternGraph = getPatternById(patternId)!!
            for (wrapper in actionsWrappers) {
                when (wrapper) {
                    is ActionWrapper.UpdateActionWrapper -> actions.add(wrapper.reconstructAction(patternGraph))
                    is ActionWrapper.DeleteActionWrapper -> actions.add(wrapper.reconstructAction(patternGraph))
                    is ActionWrapper.InsertActionWrapper -> actions.add(wrapper.reconstructAction(patternGraph))
                    is ActionWrapper.MoveActionWrapper -> actions.add(wrapper.reconstructAction(patternGraph))
                }
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return actions
    }

    private fun loadDescription(patternId: String): String? {
        return try {
            this::class.java.getResource("/patterns/$patternId/description.txt").readText()
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadLabelsGroups(patternId: String): HashMap<Int, PatternSpecificVertex.LabelsGroup> {
        val filePath = "/patterns/$patternId/labels_groups.json"
        val fileContent = this::class.java.getResource(filePath).readText()
        return Json.decodeFromString<HashMap<Int, PatternSpecificVertex.LabelsGroup>>(fileContent)
    }
}