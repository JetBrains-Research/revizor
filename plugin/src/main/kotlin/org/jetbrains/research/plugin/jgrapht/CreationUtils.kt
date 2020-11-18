package org.jetbrains.research.plugin.jgrapht

import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificEdge
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.pyflowgraph.models.PyFlowGraph
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph


/**
 * These utilities provides functionality for building `DirectedAcyclicGraph` of pattern
 * from base graph with variable nodes generalization and from original `PyFlowGraph`.
 *
 * The provided graph is something like an interlayer, because it is needed only for
 * locating isomorphic subgraphs using JGraphT library methods.
 */

fun createPatternSpecificGraph(
    baseDirectedAcyclicGraph: PatternGraph,
    labelsGroupsByVertexId: Map<Int, PatternSpecificVertex.LabelsGroup>
): PatternDirectedAcyclicGraph {
    val targetGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    val verticesMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
    for (vertex in baseDirectedAcyclicGraph.vertexSet()) {
        val newVertex = vertex.copy()
        if (vertex.label?.startsWith("var") == true) {
            newVertex.dataNodeInfo = labelsGroupsByVertexId[vertex.id] ?: PatternSpecificVertex.LabelsGroup.getEmpty()
        }
        targetGraph.addVertex(newVertex)
        verticesMapping[vertex] = newVertex
    }
    for (edge in baseDirectedAcyclicGraph.edgeSet()) {
        targetGraph.addEdge(
            verticesMapping[baseDirectedAcyclicGraph.getEdgeSource(edge)],
            verticesMapping[baseDirectedAcyclicGraph.getEdgeTarget(edge)],
            edge.copy()
        )
    }
    return targetGraph
}

fun createPatternSpecificGraph(pfg: PyFlowGraph): PatternDirectedAcyclicGraph {
    val defaultDAG = DirectedMultigraph<PatternSpecificVertex, PatternSpecificEdge>(
        PatternSpecificEdge::class.java
    )
    var edgeGlobalId = 0
    for (node in pfg.nodes) {
        val sourceVertex = PatternSpecificVertex(node)
        defaultDAG.addVertex(sourceVertex)
        for (outEdge in node.outEdges) {
            val targetVertex = PatternSpecificVertex(outEdge.nodeTo)
            if (!defaultDAG.containsVertex(targetVertex)) {
                defaultDAG.addVertex(targetVertex)
            }
            defaultDAG.addEdge(
                sourceVertex,
                targetVertex,
                PatternSpecificEdge(
                    id = edgeGlobalId++,
                    xlabel = outEdge.label,
                    fromClosure = outEdge.fromClosure
                )
            )
        }
    }
    val targetDAG = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    defaultDAG.vertexSet().forEach { targetDAG.addVertex(it) }
    for (sourceVertex in targetDAG.vertexSet()) {
        val children = defaultDAG.outgoingEdgesOf(sourceVertex).map { defaultDAG.getEdgeTarget(it) }.toSet()
        for (targetVertex in children) {
            val multipleEdge = PatternSpecificMultipleEdge(
                id = edgeGlobalId++,
                embeddedEdgeByXlabel = HashMap()
            )
            for (outEdge in defaultDAG.getAllEdges(sourceVertex, targetVertex)) {
                multipleEdge.embeddedEdgeByXlabel[outEdge.xlabel] = outEdge
            }
            targetDAG.addEdge(sourceVertex, targetVertex, multipleEdge)
        }
    }
    return targetDAG
}

fun PatternDirectedAcyclicGraph.findVertexById(id: Int): PatternSpecificVertex =
    this.vertexSet().find { it.id == id } ?: throw NoSuchElementException()