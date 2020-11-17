package org.jetbrains.research.plugin.jgrapht

import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.jgrapht.edges.MultipleEdgeComparator
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.jgrapht.vertices.WeakVertexComparator
import org.jgrapht.alg.isomorphism.VF2GraphIsomorphismInspector
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector


fun getSuperWeakSubgraphIsomorphismInspector(target: PatternGraph, pattern: PatternGraph) =
    VF2SubgraphIsomorphismInspector(
        target,
        pattern,
        compareBy<PatternSpecificVertex> { it.label },
        MultipleEdgeComparator(),
        false
    )

fun getWeakSubgraphIsomorphismInspector(target: PatternGraph, pattern: PatternGraph) =
    VF2SubgraphIsomorphismInspector(
        target,
        pattern,
        WeakVertexComparator(),
        MultipleEdgeComparator(),
        false
    )

fun getStrictGraphIsomorphismInspector(
    directedAcyclicGraph1: PatternDirectedAcyclicGraph,
    directedAcyclicGraph2: PatternDirectedAcyclicGraph
): VF2GraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge> {
    val strictVertexComparator: Comparator<PatternSpecificVertex?> =
        compareBy({ it?.originalLabel?.toLowerCase() }, { it?.label?.toLowerCase() })
    return VF2GraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        directedAcyclicGraph1,
        directedAcyclicGraph2,
        strictVertexComparator,
        MultipleEdgeComparator(),
        false
    )
}