package org.jetbrains.research.common

import org.jgrapht.graph.DefaultEdge
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

fun loadGraphFromDotFile(dotFile: File): DirectedMultigraph<Vertex, Edge> {
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
    val pfg = DirectedMultigraph<Vertex, Edge>(Edge::class.java)
    fun getVertexById(id: String) = Vertex(
        id = id,
        label = vertexAttributes[id]?.get("label")?.toString(),
        color = vertexAttributes[id]?.get("color")?.toString(),
        shape = vertexAttributes[id]?.get("shape")?.toString()
    )
    for (vertexId in temp.vertexSet()) {
        val sourceNode = getVertexById(vertexId)
        pfg.addVertex(sourceNode)
        for (outEdge in temp.outgoingEdgesOf(vertexId)) {
            val targetNode = getVertexById(temp.getEdgeTarget(outEdge))
            pfg.addVertex(targetNode)
            val edge = Edge(
                id = outEdge,
                xlabel = edgeAttributes[outEdge]?.get("xlabel")?.toString(),
                from_closure = edgeAttributes[outEdge]?.get("from_closure")?.toString()?.toBoolean(),
                style = edgeAttributes[outEdge]?.get("style")?.toString()
            )
            pfg.addEdge(sourceNode, targetNode, edge)
        }
    }
    return pfg
}
