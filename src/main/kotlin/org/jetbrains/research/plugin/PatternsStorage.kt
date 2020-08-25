package org.jetbrains.research.plugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.research.plugin.common.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarFile

object PatternsStorage {
    private val patternDescById = HashMap<String, String>()
    private val patternById =
        HashMap<String, DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()

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
            val logger = Logger.getInstance(this::class.java)
            logger.error(ex) // TODO: implement error reporter
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String): DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>? =
        patternById[patternId]

    fun getPatternDescriptionById(patternId: String): String =
        patternDescById[patternId] ?: "Unnamed pattern: $patternId"

    fun getIsomorphicPatterns(targetGraph: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>)
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
}