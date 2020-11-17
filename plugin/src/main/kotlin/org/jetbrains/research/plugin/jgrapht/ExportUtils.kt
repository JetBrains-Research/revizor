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


fun Graph<PatternSpecificVertex, PatternSpecificEdge>.export(file: File, createPdf: Boolean = false) {
    val exporter = DOTExporter<PatternSpecificVertex, PatternSpecificEdge> { v -> v.id.toString() }
    exporter.setVertexAttributeProvider { v ->
        val map = HashMap<String, Attribute>()
        map["label"] = DefaultAttribute.createAttribute("${v.label} (${v.originalLabel}) [${v.id}]")
        map["color"] = DefaultAttribute.createAttribute(v.color)
        map["shape"] = DefaultAttribute.createAttribute(v.shape)
        map["metadata"] = DefaultAttribute.createAttribute(v.metadata)
        map
    }
    exporter.setEdgeAttributeProvider { e ->
        val map = HashMap<String, Attribute>()
        map["xlabel"] = DefaultAttribute.createAttribute(e.xlabel)
        map["from_closure"] = DefaultAttribute.createAttribute(e.fromClosure)
        map
    }
    exporter.exportGraph(this, file)
    if (createPdf) {
        val builder = ProcessBuilder().also {
            it.command("dot", "-Tps", file.absolutePath, "-o", "${file.absolutePath}.pdf")
        }
        val process = builder.start()
        process.waitFor()
    }
}

fun DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>.export(
    file: File,
    createPdf: Boolean = false
) {
    val targetGraph = DirectedMultigraph<PatternSpecificVertex, PatternSpecificEdge>(PatternSpecificEdge::class.java)
    this.vertexSet().forEach { targetGraph.addVertex(it) }
    for (multipleEdge in this.edgeSet()) {
        for (edge in multipleEdge.embeddedEdgeByXlabel.values) {
            targetGraph.addEdge(
                this.getEdgeSource(multipleEdge),
                this.getEdgeTarget(multipleEdge),
                edge
            )
        }
    }
    targetGraph.export(file, createPdf)
}
