package org.jetbrains.research.plugin.jgrapht

import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificEdge
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.dot.DOTImporter
import java.io.InputStream

fun loadPatternSpecificGraph(dotInput: InputStream): PatternDirectedAcyclicGraph {
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
                shape = vertexAttributes[vertexId]?.get("shape")?.toString(),
                metadata = vertexAttributes[vertexId]?.get("metadata")?.toString() ?: "",
                kind = vertexAttributes[vertexId]?.get("kind")?.toString()
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