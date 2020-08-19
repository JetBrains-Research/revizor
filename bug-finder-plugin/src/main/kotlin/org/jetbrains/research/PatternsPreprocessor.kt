package org.jetbrains.research

import com.google.gson.Gson
import com.intellij.openapi.components.service
import org.jetbrains.research.ide.BugFinderConfigService
import org.jetbrains.research.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.jgrapht.PatternSpecificVertex
import org.jgrapht.Graph
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.nio.file.Path
import java.nio.file.Paths


object PatternsPreprocessor {
    private val patternsStoragePath: Path =
        service<BugFinderConfigService>().state.patternsOutputPath
    private val finalPatternGraphByPatternDirPath =
        HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
    private val patternFragmentsGraphsByPatternDirPath =
        HashMap<Path, ArrayList<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()

    fun getPatternGraphByPath(pathToPatternDir: Path) = finalPatternGraphByPatternDirPath[pathToPatternDir]

    internal fun createPatternGraph(
        baseGraph: Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
        variableLabelsGroups: ArrayList<HashSet<String>>
    ): DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge> {
        val targetGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
            PatternSpecificMultipleEdge::class.java
        )
        val verticesMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
        var variableVerticesCounter = 0
        for (vertex in baseGraph.vertexSet()) {
            val newVertex = vertex.copy()
            if (vertex.label?.startsWith("var") == true) {
                newVertex.possibleVarLabels = variableLabelsGroups.getOrNull(variableVerticesCounter) ?: hashSetOf()
                variableVerticesCounter++
            }
            targetGraph.addVertex(newVertex)
            verticesMapping[vertex] = newVertex
        }
        for (edge in baseGraph.edgeSet()) {
            targetGraph.addEdge(
                verticesMapping[baseGraph.getEdgeSource(edge)],
                verticesMapping[baseGraph.getEdgeTarget(edge)],
                edge.copy()
            )
        }
        return targetGraph
    }

    private fun loadOriginalPatterns() {
        patternsStoragePath.toFile().walk().forEach {
            if (it.isFile && it.extension == "dot" && it.name.startsWith("fragment")) {
                val currentGraph = PatternSpecificGraphsLoader.loadDAGFromDotInputStream(it.inputStream())
                val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                    currentGraph,
                    currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
                )
                patternFragmentsGraphsByPatternDirPath.getOrPut(Paths.get(it.parent)) { ArrayList() }
                    .add(subgraphBefore)
            }
        }
    }

    private fun collectVariableLabelsGroups(fragments: List<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>)
            : ArrayList<HashSet<String>> {
        val variableLabelsGroups = ArrayList<HashSet<String>>()
        for (graph in fragments) {
            var varsCnt = 0
            for (vertex in graph.vertexSet()) {
                if (vertex.label?.startsWith("var") == true) {
                    if (varsCnt >= variableLabelsGroups.size) {
                        variableLabelsGroups.add(hashSetOf<String>())
                    } else {
                        variableLabelsGroups[varsCnt].add(vertex.originalLabel!!)
                    }
                    varsCnt++
                }
            }
        }
        return variableLabelsGroups
    }

    private fun processVariableLabelsGroups() {
        for ((pathToPatternDir, fragmentsGraphs) in patternFragmentsGraphsByPatternDirPath) {
            finalPatternGraphByPatternDirPath[pathToPatternDir] =
                createPatternGraph(
                    baseGraph = fragmentsGraphs.first(),
                    variableLabelsGroups = collectVariableLabelsGroups(fragmentsGraphs)
                )
        }
    }

    private fun saveVariableLabelsGroups() {
        for ((pathToPatternDir, graph) in finalPatternGraphByPatternDirPath) {
            val varLabels = ArrayList<HashSet<String>>()
            for (vertex in graph.vertexSet()) {
                if (vertex.label?.startsWith("var") == true) {
                    varLabels.add(vertex.possibleVarLabels)
                }
            }
            val varLabelsJsonString = Gson().toJson(varLabels)
            val varLabelsFile = pathToPatternDir.resolve("possible_variable_labels.json").toFile()
            varLabelsFile.writeText(varLabelsJsonString)
        }
    }

}