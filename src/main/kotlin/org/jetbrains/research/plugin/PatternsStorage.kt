package org.jetbrains.research.plugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarFile

typealias PatternGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>

/**
 * A singleton class for storing graph patterns.
 *
 * This class provides functionality for retrieving patterns from JAR file,
 * extracting descriptions and common variables' labels for each pattern. Also,
 * it has `getIsomorphicPatterns` method which is used for patterns localization.
 */
object PatternsStorage {
    private val patternDescById = HashMap<String, String>()
    private val patternById = HashMap<String, PatternGraph>()
    private val psiNodeMappingById = HashMap<String, HashMap<PatternSpecificVertex, PyElement?>>()
    private val logger = Logger.getInstance(this::class.java)
    lateinit var project: Project

    init {
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
                    val currentGraph = createPatternSpecificGraph(dotSrcStream)
                    val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                        currentGraph,
                        currentGraph.vertexSet()
                            .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                            .toSet()
                    )
                    val variableLabelsGroups = loadVariableLabelsGroups(patternId) ?: arrayListOf()
                    val targetGraph = createPatternSpecificGraph(subgraphBefore, variableLabelsGroups)
                    patternById[patternId] = targetGraph
                    patternDescById[patternId] = loadDescription(patternId) ?: "No description provided"
                }
            }
        } catch (ex: Exception) {
            logger.warn("Failed to load patterns resources from JAR file")
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String): PatternGraph? =
        patternById[patternId]

    fun getPatternDescriptionById(patternId: String): String =
        patternDescById[patternId] ?: "Unnamed pattern: $patternId"

    fun getPatternPsiNodesMappingById(patternId: String, vertex: PatternSpecificVertex): PyElement? =
        psiNodeMappingById[patternId]?.get(vertex)

    fun getIsomorphicPatterns(targetGraph: PatternGraph)
            : HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>> {
        val suitablePatterns =
            HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()
        for ((patternId, graph) in patternById) {
            val inspector = getWeakSubgraphIsomorphismInspector(targetGraph, graph)
            if (inspector.isomorphismExists()) {
                for (mapping in inspector.mappings) {
                    suitablePatterns.getOrPut(patternId) { arrayListOf() }.add(mapping)
                }
            }
        }
        return suitablePatterns
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

    private fun loadPsiPattern(
        patternId: String,
        patternGraph: PatternGraph
    ): Map<PyElement, PatternSpecificVertex> {
        val vertexByPyElement: HashMap<PyElement, PatternSpecificVertex> = hashMapOf()
        try {
            val filePath = "/patterns/$patternId/before.py"
            val fileContent = this::class.java.getResource(filePath).readText()
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(PythonLanguage.getInstance(), fileContent)
            val mainFunctionPsi = psiFile.children.first() as PyFunction
            val mainFunctionJGraph = buildPyFlowGraphForMethod(mainFunctionPsi, builder = "kotlin")
            val inspector = getWeakSubgraphIsomorphismInspector(mainFunctionJGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().first()
                for (vertex in patternGraph.vertexSet()) {
                    val mappedVertex = mapping.getVertexCorrespondence(vertex, false)
                    if (mappedVertex.origin?.psi != null) {
                        vertexByPyElement[mappedVertex.origin?.psi!!] = vertex
                    }
                }
            } else {
                logger.error("Pattern's graph doesn't match pattern's function code snippet (before.py)")
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return vertexByPyElement
    }
}