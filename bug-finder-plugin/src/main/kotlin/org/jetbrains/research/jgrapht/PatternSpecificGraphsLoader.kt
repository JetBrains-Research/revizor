package org.jetbrains.research.jgrapht

import org.jetbrains.research.pyflowgraph.models.PyFlowGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.dot.DOTImporter
import java.io.InputStream


object PatternSpecificGraphsLoader {

    fun loadDAGFromPyFlowGraph(pfg: PyFlowGraph):
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

    fun loadDAGFromDotInputStream(dotInput: InputStream)
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
            targetDAG.addVertex(
                PatternSpecificVertex(
                    id = vertexId,
                    label = vertexAttributes[vertexId]?.get("label")?.toString()
                        ?.substringBefore('(')
                        ?.trim(),
                    originalLabel = vertexAttributes[vertexId]?.get("label")?.toString()
                        ?.substringAfter('(')
                        ?.substringBefore(')')
                        ?.trim(),
                    color = vertexAttributes[vertexId]?.get("color")?.toString(),
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
                    targetDAG.vertexSet().find { it.id == sourceVertexId },
                    targetDAG.vertexSet().find { it.id == targetVertexId },
                    multipleEdge
                )
            }
        }
        return targetDAG
    }
}