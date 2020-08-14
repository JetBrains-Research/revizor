package org.jetbrains.research.common

import org.jetbrains.research.jgrapht.*
import org.jgrapht.Graph
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector
import org.jgrapht.graph.DirectedAcyclicGraph

fun getLongestCommonSuffix(strings: ArrayList<String?>?): String {
    if (strings == null || strings.isEmpty())
        return ""
    var lcs = strings.first()
    for (string in strings) {
        lcs = lcs?.commonSuffixWith(string ?: "")
    }
    return lcs ?: ""
}

fun weakSubGraphIsomorphismExists(
    target: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    pattern: Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>
): Boolean {
    val isomorphismInspector =
        VF2SubgraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
            target,
            pattern,
            WeakVertexComparator(),
            MultipleEdgeComparator(),
            false
        )
    return isomorphismInspector.isomorphismExists()
}

fun strictGraphIsomorphismExists(
    graph1: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    graph2: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>
): Boolean {
    val isomorphismInspector =
        VF2GraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
            graph1,
            graph2,
            StrictVertexComparator(),
            MultipleEdgeComparator(),
            false
        )
    return isomorphismInspector.isomorphismExists()
}