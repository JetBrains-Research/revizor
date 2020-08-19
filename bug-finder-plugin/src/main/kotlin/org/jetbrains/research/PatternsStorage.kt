package org.jetbrains.research

import com.google.gson.Gson
import org.jetbrains.research.common.getWeakSubGraphIsomorphismInspector
import org.jetbrains.research.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.jgrapht.PatternSpecificVertex
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.jar.JarFile

object PatternsStorage {
    private val patternById =
        HashMap<String, DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
    private val patternDescById = HashMap<String, String>()

    init {
        var jar: JarFile? = null
        try {
            val file = File(this::class.java.getResource("").path)
            val jarFilePath = file.parentFile.parentFile.parent
                .replace("(!|file:\\\\)".toRegex(), "")
                .replace("file:/", "/")
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
                    val currentGraph = PatternSpecificGraphsLoader.loadDAGFromDotInputStream(dotSrcStream)
                    val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                        currentGraph,
                        currentGraph.vertexSet()
                            .filter { vertex -> vertex.color == "red2" }
                            .toSet()
                    )
                    val variableLabelsGroups = loadVariableLabelsGroups(patternId) ?: arrayListOf()
                    val targetGraph = PatternsPreprocessor.createPatternGraph(subgraphBefore, variableLabelsGroups)
                    patternById[patternId] = targetGraph
                    patternDescById[patternId] = loadDescription(patternId) ?: "No description provided"
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
        val filePath = "/patterns/$patternId/description.txt"
        val stream = this::class.java.getResourceAsStream(filePath)
        return InputStreamReader(stream).use { inputStreamReader ->
            BufferedReader(inputStreamReader).use { bufferedReader ->
                bufferedReader.readText()
            }
        }
    }

    private fun loadVariableLabelsGroups(patternId: String): ArrayList<HashSet<String>>? {
        val filePath = "/patterns/$patternId/possible_variable_labels.json"
        val stream = this::class.java.getResourceAsStream(filePath)
        val fileContent = InputStreamReader(stream).use { inputStreamReader ->
            BufferedReader(inputStreamReader).use { bufferedReader ->
                bufferedReader.readText()
            }
        }
        val json = Gson().fromJson(fileContent, ArrayList<ArrayList<String>>()::class.java)
        val varLabelsGroups = ArrayList<HashSet<String>>()
        json.forEach { varLabelsGroups.add(it.toHashSet()) }
        return varLabelsGroups
    }
}