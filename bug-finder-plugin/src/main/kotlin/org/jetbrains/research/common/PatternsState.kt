package org.jetbrains.research.common

import org.jetbrains.research.localization.loadDAGFromDotFile
import org.jgrapht.Graph
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.nio.file.Path

object PatternsState {
    private val patternGraphByPath = HashMap<Path, Graph<Vertex, MultipleEdge>>()

    init {
        val patternsGlobalDirs = HashSet<String>()
        BugFinderConfig.patternsOutputPath.toFile().walk().forEach {
            if (it.isFile.and(it.extension == "dot").and(it.name.startsWith("fragment"))) {
                if (!patternsGlobalDirs.contains(it.parent)) {
                    patternsGlobalDirs.add(it.parent)
                    val currentGraph = loadDAGFromDotFile(it)
                    val subgraphBefore = AsSubgraph<Vertex, MultipleEdge>(
                        currentGraph,
                        currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
                    )
                    patternGraphByPath[it.toPath()] = subgraphBefore
                }
            }
        }
    }

    fun getIsomorphicPatterns(targetGraph: DirectedAcyclicGraph<Vertex, MultipleEdge>)
            : Map<Path, Graph<Vertex, MultipleEdge>> {
        val suitablePatterns = HashMap<Path, Graph<Vertex, MultipleEdge>>()
        for (entry in patternGraphByPath) {
            val patternPath = entry.key
            val patternGraph = entry.value
            val isomorphismInspector = VF2SubgraphIsomorphismInspector<Vertex, MultipleEdge>(
                targetGraph, patternGraph, VertexComparator(), MultipleEdgeComparator(), false
            )
            if (isomorphismInspector.isomorphismExists()) {
                suitablePatterns[patternPath] = patternGraph
            }
        }
        return suitablePatterns
    }
}