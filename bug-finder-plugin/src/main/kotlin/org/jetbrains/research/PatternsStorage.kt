package org.jetbrains.research

import com.intellij.openapi.components.service
import org.jetbrains.research.ide.BugFinderConfigService
import org.jetbrains.research.jgrapht.*
import org.jgrapht.Graph
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.nio.file.Path
import java.nio.file.Paths

object PatternsStorage {
    private val patternsStoragePath: Path = service<BugFinderConfigService>().state.patternsOutputPath
    private val unifiedPatternGraphByPatternDirPath =
        HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
    private val patternsGraphsByPatternsDirPath =
        HashMap<Path, ArrayList<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()

    init {
        loadPatterns()
        unifyVarsOriginalLabels()
    }

    private fun loadPatterns() {
        patternsStoragePath.toFile().walk().forEach {
            if (it.isFile && it.extension == "dot" && it.name.startsWith("fragment")) {
                val currentGraph = PatternSpecificGraphsLoader.loadDAGFromDotFile(it)
                val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                    currentGraph,
                    currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
                )
                patternsGraphsByPatternsDirPath.getOrPut(Paths.get(it.parent)) { ArrayList() }
                    .add(subgraphBefore)
            }
        }
    }

    private fun unifyVarsOriginalLabels() {
        for (entry in patternsGraphsByPatternsDirPath) {
            val pathToPatternDir = entry.key
            val fragmentsGraphs = entry.value
            val variableOriginalLabelsGroups = HashMap<Int, ArrayList<String?>>()
            for (graph in fragmentsGraphs) {
                var variableVerticesCounter = 0
                for (vertex in graph.vertexSet()) {
                    if (vertex.label?.startsWith("var") == true) {
                        variableOriginalLabelsGroups.getOrPut(variableVerticesCounter) { ArrayList() }
                            .add(vertex.originalLabel)
                        variableVerticesCounter++
                    }
                }
            }
            val baseGraphForUnification = fragmentsGraphs.first()
            val unifiedGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                PatternSpecificMultipleEdge::class.java
            )
            val verticesMap = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
            var variableVerticesCounter = 0
            for (vertex in baseGraphForUnification.vertexSet()) {
                val newVertex = vertex.copy()
                if (vertex.label?.startsWith("var") == true) {
                    newVertex.longestCommonSuffix = getLongestCommonSuffix(
                        variableOriginalLabelsGroups[variableVerticesCounter]
                    )
                    variableVerticesCounter++
                }
                unifiedGraph.addVertex(newVertex)
                verticesMap[vertex] = newVertex
            }
            for (edge in baseGraphForUnification.edgeSet()) {
                unifiedGraph.addEdge(
                    verticesMap[baseGraphForUnification.getEdgeSource(edge)],
                    verticesMap[baseGraphForUnification.getEdgeTarget(edge)],
                    edge.copy()
                )
            }
            unifiedPatternGraphByPatternDirPath[pathToPatternDir] = unifiedGraph
        }
    }

    fun getDescription(pathToPatternDir: Path): String? {
        val descriptionFile = pathToPatternDir.resolve("description.txt").toFile()
        return if (descriptionFile.exists()) {
            descriptionFile.readText()
        } else {
            null
        }
    }

    fun getIsomorphicPatterns(targetGraph: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>)
            : Map<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>> {
        val suitablePatterns = HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
        for (entry in unifiedPatternGraphByPatternDirPath) {
            val pathToPatternDir = entry.key
            val unifiedPatternGraph = entry.value
            val isomorphismInspector =
                VF2SubgraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                    targetGraph, unifiedPatternGraph,
                    PatternVertexComparator(),
                    MultipleEdgeComparator(), false
                )
            if (isomorphismInspector.isomorphismExists()) {
                suitablePatterns[pathToPatternDir] = unifiedPatternGraph
            }
        }
        return suitablePatterns
    }
}