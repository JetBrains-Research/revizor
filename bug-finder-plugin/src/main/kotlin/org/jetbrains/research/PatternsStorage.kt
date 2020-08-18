package org.jetbrains.research

import com.intellij.openapi.components.service
import org.jetbrains.research.common.getWeakSubGraphIsomorphismInspector
import org.jetbrains.research.ide.BugFinderConfigService
import org.jetbrains.research.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.jgrapht.PatternSpecificVertex
import org.jgrapht.Graph
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.nio.file.Path
import java.nio.file.Paths

object PatternsStorage {
    private val patternsStoragePath: Path = service<BugFinderConfigService>().state.patternsOutputPath
    private val finalPatternGraphByPatternDirPath =
        HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
    private val patternFragmentsGraphsByPatternsDirPath =
        HashMap<Path, ArrayList<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()

    init {
        loadPatterns()
        extractCommonVariableLabels()
    }

    fun getPatternGraphByPath(pathToPatternDir: Path) = finalPatternGraphByPatternDirPath[pathToPatternDir]

    private fun loadPatterns() {
        patternsStoragePath.toFile().walk().forEach {
            if (it.isFile && it.extension == "dot" && it.name.startsWith("fragment")) {
                val currentGraph = PatternSpecificGraphsLoader.loadDAGFromDotFile(it)
                val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                    currentGraph,
                    currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
                )
                patternFragmentsGraphsByPatternsDirPath.getOrPut(Paths.get(it.parent)) { ArrayList() }
                    .add(subgraphBefore)
            }
        }
    }

    private fun extractCommonVariableLabels() {
        for ((pathToPatternDir, fragmentsGraphs) in patternFragmentsGraphsByPatternsDirPath) {
            val variableOriginalLabelsGroups = HashMap<Int, HashSet<String>>()
            for (graph in fragmentsGraphs) {
                var variableVerticesCounter = 0
                for (vertex in graph.vertexSet()) {
                    if (vertex.label?.startsWith("var") == true) {
                        variableOriginalLabelsGroups.getOrPut(variableVerticesCounter) { hashSetOf() }
                            .add(vertex.originalLabel!!)
                        variableVerticesCounter++
                    }
                }
            }
            val baseGraph = fragmentsGraphs.first()
            val finalGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                PatternSpecificMultipleEdge::class.java
            )
            val verticesMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
            var variableVerticesCounter = 0
            for (vertex in baseGraph.vertexSet()) {
                val newVertex = vertex.copy()
                if (vertex.label?.startsWith("var") == true) {
                    newVertex.possibleVarNames = variableOriginalLabelsGroups[variableVerticesCounter]!!
                    variableVerticesCounter++
                }
                finalGraph.addVertex(newVertex)
                verticesMapping[vertex] = newVertex
            }
            for (edge in baseGraph.edgeSet()) {
                finalGraph.addEdge(
                    verticesMapping[baseGraph.getEdgeSource(edge)],
                    verticesMapping[baseGraph.getEdgeTarget(edge)],
                    edge.copy()
                )
            }
            finalPatternGraphByPatternDirPath[pathToPatternDir] = finalGraph
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
            : HashMap<Path, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>> {
        val suitablePatterns = HashMap<Path, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
        for (entry in finalPatternGraphByPatternDirPath) {
            val pathToPatternDir = entry.key
            val unifiedPatternGraph = entry.value
            val inspector = getWeakSubGraphIsomorphismInspector(targetGraph, unifiedPatternGraph)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().iterator().next()
                if (mapping != null) {
                    suitablePatterns[pathToPatternDir] = mapping
                }
            }
        }
        return suitablePatterns
    }
}