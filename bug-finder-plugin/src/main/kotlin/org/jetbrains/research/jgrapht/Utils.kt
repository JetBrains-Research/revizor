package org.jetbrains.research.jgrapht

import com.intellij.openapi.components.service
import org.jetbrains.research.ide.BugFinderConfigService
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.DefaultAttribute
import org.jgrapht.nio.dot.DOTExporter
import java.nio.file.Paths

fun exportDotFile(graph: DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificEdge>) {
    val exporter = DOTExporter<PatternSpecificVertex, PatternSpecificEdge> { v -> v.id }
    exporter.setVertexAttributeProvider { v ->
        val map = HashMap<String, Attribute>()
        map["label"] = DefaultAttribute.createAttribute(v.label)
        map["original_label"] = DefaultAttribute.createAttribute(v.originalLabel)
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
    exporter.exportGraph(
        graph,
        service<BugFinderConfigService>().state.tempDirectory
            .resolve(Paths.get("${graph.hashCode()}.dot"))
            .toFile()
    )
}