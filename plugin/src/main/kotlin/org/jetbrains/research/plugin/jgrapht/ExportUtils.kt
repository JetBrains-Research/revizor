package org.jetbrains.research.plugin.jgrapht

import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificEdge
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.Graph
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.io.File


fun exportDotFile(graph: Graph<PatternSpecificVertex, PatternSpecificEdge>, file: File) {
    val exporter = DOTExporter<PatternSpecificVertex, PatternSpecificEdge> { v -> v.id.toString() }
    exporter.setVertexAttributeProvider { v ->
        val map = HashMap<String, Attribute>()
        map["label"] = DefaultAttribute.createAttribute("${v.label} (${v.originalLabel}) []")
        map["color"] = DefaultAttribute.createAttribute(v.color)
        map["shape"] = DefaultAttribute.createAttribute(v.shape)
        map
    }
    exporter.setEdgeAttributeProvider { e ->
        val map = HashMap<String, Attribute>()
        map["xlabel"] = DefaultAttribute.createAttribute(e.xlabel)
        map["from_closure"] = DefaultAttribute.createAttribute(e.fromClosure)
        map
    }
    exporter.exportGraph(graph, file)
    val builder = ProcessBuilder().also {
        it.command("dot", "-Tps", file.absolutePath, "-o", "${file.absolutePath}.pdf")
    }
    val process = builder.start()
    process.waitFor()
}

fun exportDotFileForGraphWithMultipleEdges(
    graphWithMultipleEdges: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>,
    file: File
) {
    val targetGraph = DirectedMultigraph<PatternSpecificVertex, PatternSpecificEdge>(PatternSpecificEdge::class.java)
    graphWithMultipleEdges.vertexSet().forEach { targetGraph.addVertex(it) }
    for (multipleEdge in graphWithMultipleEdges.edgeSet()) {
        for (edge in multipleEdge.embeddedEdgeByXlabel.values) {
            targetGraph.addEdge(
                graphWithMultipleEdges.getEdgeSource(multipleEdge),
                graphWithMultipleEdges.getEdgeTarget(multipleEdge),
                edge
            )
        }
    }
    exportDotFile(targetGraph, file)
}
