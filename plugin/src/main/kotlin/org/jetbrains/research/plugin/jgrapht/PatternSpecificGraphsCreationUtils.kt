package org.jetbrains.research.plugin.jgrapht

import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificEdge
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.pyflowgraph.models.PyFlowGraph
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.dot.DOTImporter
import java.io.InputStream


/**
 * These utilities provides functionality for building `DirectedAcyclicGraph` of pattern
 * from raw existing `.dot` files, from base graph with variable nodes generalization and
 * from original `PyFlowGraph`. The provided DAG is something like an interlayer, because
 * it is needed only for locating isomorphic subgraphs using JGraphT library methods.
 */

fun createPatternSpecificGraph(
    baseGraph: Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    variableLabelsGroups: ArrayList<PatternSpecificVertex.LabelsGroup>
): DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge> {
    val targetGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    val verticesMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
    var variableVerticesCounter = 0
    for (vertex in baseGraph.vertexSet()) {
        val newVertex = vertex.copy()
        if (vertex.label?.startsWith("var") == true) {
            newVertex.dataNodeInfo = variableLabelsGroups.getOrNull(variableVerticesCounter)
                ?: PatternSpecificVertex.LabelsGroup(
                    whatMatters = PatternSpecificVertex.LabelsGroup.Indicator.UNKNOWN,
                    labels = hashSetOf(),
                    longestCommonSuffix = ""
                )
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

fun createPatternSpecificGraph(pfg: PyFlowGraph):
        DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge> {
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

fun createPatternSpecificGraph(dotInput: InputStream)
        : DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge> {
    val importer = DOTImporter<String, DefaultEdge>()
    importer.setVertexFactory { id -> id }
    val vertexAttributes = HashMap<String, HashMap<String, Attribute>>()
    val edgeAttributes = HashMap<DefaultEdge, HashMap<String, Attribute>>()
    importer.addVertexAttributeConsumer { pair, attr ->
        vertexAttributes.getOrPut(pair.first) { HashMap() }[pair.second] = attr
    }
    importer.addEdgeAttributeConsumer { pair, attr ->
        edgeAttributes.getOrPut(pair.first) { HashMap() }[pair.second] = attr
    }
    val importedDAG = DirectedMultigraph<String, DefaultEdge>(DefaultEdge::class.java)
    importer.importGraph(importedDAG, dotInput)

    val targetDAG = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
        PatternSpecificMultipleEdge::class.java
    )
    for (vertexId in importedDAG.vertexSet()) {
        val vertexColor = vertexAttributes[vertexId]?.get("color")?.toString()
        targetDAG.addVertex(
            PatternSpecificVertex(
                id = vertexId.toInt(),
                label = vertexAttributes[vertexId]?.get("label")?.toString()
                    ?.substringBefore('(')
                    ?.trim(),
                originalLabel = vertexAttributes[vertexId]?.get("label")?.toString()
                    ?.substringAfter('(')
                    ?.substringBefore(')')
                    ?.trim(),
                fromPart = if (vertexColor == "red2")
                    PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE
                else
                    PatternSpecificVertex.ChangeGraphPartIndicator.AFTER,
                color = vertexColor,
                shape = vertexAttributes[vertexId]?.get("shape")?.toString()
            )
        )
    }
    var edgeGlobalId = 0
    for (sourceVertexId in importedDAG.vertexSet()) {
        val children = importedDAG.outgoingEdgesOf(sourceVertexId)
            .map { importedDAG.getEdgeTarget(it) }
            .toSet()
        for (targetVertexId in children) {
            val multipleEdge = PatternSpecificMultipleEdge(
                id = edgeGlobalId++,
                embeddedEdgeByXlabel = HashMap()
            )
            for (outEdge in importedDAG.getAllEdges(sourceVertexId, targetVertexId)) {
                val edge = PatternSpecificEdge(
                    id = edgeGlobalId++,
                    xlabel = edgeAttributes[outEdge]?.get("xlabel")?.toString(),
                    fromClosure = edgeAttributes[outEdge]?.get("from_closure")?.toString()?.toBoolean(),
                    style = edgeAttributes[outEdge]?.get("style")?.toString()
                )
                multipleEdge.embeddedEdgeByXlabel[edge.xlabel] = edge
            }
            targetDAG.addEdge(
                targetDAG.vertexSet().find { it.id == sourceVertexId.toInt() },
                targetDAG.vertexSet().find { it.id == targetVertexId.toInt() },
                multipleEdge
            )
        }
    }
    return targetDAG
}
