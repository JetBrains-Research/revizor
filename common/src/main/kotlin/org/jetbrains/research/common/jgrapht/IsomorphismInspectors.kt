package org.jetbrains.research.common.jgrapht

import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.jgrapht.edges.MultipleEdgeComparator
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.common.jgrapht.vertices.WeakVertexComparator
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

fun getStrictGraphIsomorphismInspector(graph1: PatternGraph, graph2: PatternGraph) =
    VF2GraphIsomorphismInspector<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        graph1,
        graph2,
        compareBy({ it?.originalLabel?.toLowerCase() }, { it?.label?.toLowerCase() }),
        MultipleEdgeComparator(),
        false
    )
