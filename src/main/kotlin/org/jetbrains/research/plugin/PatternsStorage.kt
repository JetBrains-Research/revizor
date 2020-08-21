package org.jetbrains.research.plugin

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.research.plugin.common.getWeakSubGraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.PatternSpecificGraphFactory
import org.jetbrains.research.plugin.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.PatternSpecificVertex
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.jar.JarFile

object PatternsStorage {
    private val patternDescById = HashMap<String, String>()
    private val patternById =
        HashMap<String, DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()

    init {
        var jar: JarFile? = null
        try {
            val file = File(this::class.java.getResource("").path)
            val jarFilePath = file.parentFile.parentFile.parentFile.parent
                .replace("(!|file:\\\\)".toRegex(), "")
                .replace("file:/", "/")  // FIXME
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
                    val currentGraph = PatternSpecificGraphFactory.createGraph(dotSrcStream)
                    val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                        currentGraph,
                        currentGraph.vertexSet()
                            .filter { vertex -> vertex.color == "red2" }
                            .toSet()
                    )
                    val variableLabelsGroups = loadVariableLabelsGroups(patternId) ?: arrayListOf()
                    val targetGraph = PatternSpecificGraphFactory.createGraph(subgraphBefore, variableLabelsGroups)
                    patternById[patternId] = targetGraph
                    patternDescById[patternId] =
                        loadDescription(patternId) ?: "No description provided"
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String) = patternById[patternId]

    fun getPatternDescriptionById(patternId: String) = patternDescById[patternId]

    fun getIsomorphicPatterns(targetGraph: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>)
            : HashMap<String, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>> {
        val suitablePatterns = HashMap<String, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
        for ((patternId, graph) in patternById) {
            val inspector = getWeakSubGraphIsomorphismInspector(targetGraph, graph)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().iterator().next()
                if (mapping != null) {
                    suitablePatterns[patternId] = mapping
                }
            }
        }
        return suitablePatterns
    }

    private fun loadDescription(patternId: String): String? {
        return try {
            val filePath = "/patterns/$patternId/description.txt"
            val stream = this::class.java.getResourceAsStream(filePath)
            InputStreamReader(stream).use { inputStreamReader ->
                BufferedReader(inputStreamReader).use { bufferedReader ->
                    bufferedReader.readText()
                }
            }
        } catch (ex: NullPointerException) {
            null
        }
    }

    private fun loadVariableLabelsGroups(patternId: String): ArrayList<PatternSpecificVertex.LabelsGroup>? {
        return try {
            val filePath = "/patterns/$patternId/possible_variable_labels.json"
            val stream = this::class.java.getResourceAsStream(filePath)
            val fileContent = InputStreamReader(stream).use { inputStreamReader ->
                BufferedReader(inputStreamReader).use { bufferedReader ->
                    bufferedReader.readText()
                }
            }
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
        } catch (ex: NullPointerException) {
            null
        }
    }
}