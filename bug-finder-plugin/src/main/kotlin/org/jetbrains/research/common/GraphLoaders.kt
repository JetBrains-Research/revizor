package org.jetbrains.research.common

import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.DirectedMultigraph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.dot.DOTImporter
import java.io.File


fun createAndSavePyFlowGraph(inputFile: File): File {
    val pythonExecPath = Config.pythonExecutablePath.toString()
    val mainScriptPath = Config.codeChangeMinerPath.resolve("main.py").toString()
    val inputFilePath = inputFile.absolutePath
    val outputFilePath = Config.tempDirectory.toPath()
        .resolve("pfg_${inputFile.nameWithoutExtension}.dot")
        .toString()
    val builder = ProcessBuilder().also {
        it.command(pythonExecPath, mainScriptPath, "pfg", "-i", inputFilePath, "-o", outputFilePath)
    }
    builder.start().also { it.waitFor() }
    val dotFile = File(outputFilePath)
    val dotPdfFile = File(dotFile.absolutePath.plus(".pdf"))
    dotFile.deleteOnExit()
    dotPdfFile.deleteOnExit()
    return dotFile
}

fun loadGraphFromDotFile(dotFile: File): DirectedAcyclicGraph<Vertex, MultipleEdge> {
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
    val temp = DirectedMultigraph<String, DefaultEdge>(DefaultEdge::class.java)
    importer.importGraph(temp, dotFile)

    val pfg = DirectedAcyclicGraph<Vertex, MultipleEdge>(MultipleEdge::class.java)
    fun getVertexById(id: String) = Vertex(
        id = id,
        label = vertexAttributes[id]?.get("label")?.toString()
            ?.substringBefore('(')
            ?.strip(),
        originalLabel = vertexAttributes[id]?.get("label")?.toString()
            ?.substringAfter('(')
            ?.substringBefore(')')
            ?.strip(),
        color = vertexAttributes[id]?.get("color")?.toString(),
        shape = vertexAttributes[id]?.get("shape")?.toString()
    )
    for (vertexId in temp.vertexSet()) {
        val sourceVertex = getVertexById(vertexId)
        pfg.addVertex(sourceVertex)
    }
    for (sourceVertexId in temp.vertexSet()) {
        val childrenGroups = temp.outgoingEdgesOf(sourceVertexId)
            .map { temp.getEdgeTarget(it) }
            .groupBy { it }
        for (entry in childrenGroups) {
            val targetVertexId = entry.key
            val group = entry.value
            val multipleEdge = MultipleEdge(
                id = temp.getEdge(sourceVertexId, targetVertexId),
                embeddedEdgeByXlabel = HashMap()
            )
            for (outEdge in temp.getAllEdges(sourceVertexId, targetVertexId)) {
                val edge = Edge(
                    id = outEdge,
                    xlabel = edgeAttributes[outEdge]?.get("xlabel")?.toString(),
                    fromClosure = edgeAttributes[outEdge]?.get("from_closure")?.toString()?.toBoolean(),
                    style = edgeAttributes[outEdge]?.get("style")?.toString()
                )
                multipleEdge.embeddedEdgeByXlabel[edge.xlabel] = edge
            }
            pfg.addEdge(
                pfg.vertexSet().find { it.id == sourceVertexId },
                pfg.vertexSet().find { it.id == targetVertexId },
                multipleEdge
            )
        }
    }
    return pfg
}
