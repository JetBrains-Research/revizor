package org.jetbrains.research.plugin.common

import org.jetbrains.research.plugin.jgrapht.*
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

fun getWeakSubGraphIsomorphismInspector(
    target: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    pattern: Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>
) =
    VF2SubgraphIsomorphismInspector(
        target,
        pattern,
        WeakVertexComparator(),
        MultipleEdgeComparator(),
        false
    )


fun getStrictGraphIsomorphismInspector(
    graph1: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    graph2: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>
) =
    VF2GraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        graph1,
        graph2,
        StrictVertexComparator(),
        MultipleEdgeComparator(),
        false
    )