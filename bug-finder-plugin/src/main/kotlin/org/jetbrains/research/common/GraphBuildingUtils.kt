package org.jetbrains.research.common

import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.Config
import org.jetbrains.research.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.jgrapht.PatternSpecificVertex
import org.jetbrains.research.pyflowgraph.GraphBuildingException
import org.jetbrains.research.pyflowgraph.PyFlowGraphBuilder
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File


fun buildPyFlowGraphForMethod(node: PyFunction, builder: String = "kotlin")
        : DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge> =
    when (builder) {
        "python" -> {
            val tempFile = createTempFileFromMethodPsi(node)
            val dotFile = buildPyFlowGraphBySubprocess(tempFile)
            PatternSpecificGraphsLoader.loadDAGFromDotInputStream(dotFile.inputStream())
        }
        "kotlin" -> {
            val methodPyFlowGraph = PyFlowGraphBuilder().buildForPyFunction(node)
            PatternSpecificGraphsLoader.loadDAGFromPyFlowGraph(methodPyFlowGraph)
        }
        else -> throw  IllegalArgumentException()
    }

fun createTempFileFromMethodPsi(node: PyFunction): File {
    val currentMethodSrc: String = node.originalElement.text
    val tempFile = createTempFile(
        prefix = "method_${node.name}_",
        directory = Config.TEMP_DIRECTORY_PATH.toFile()
    )
    tempFile.writeText(currentMethodSrc)
    tempFile.deleteOnExit()
    return tempFile
}

fun buildPyFlowGraphBySubprocess(inputFile: File): File {
    val pythonExecPath = Config.PYTHON_EXECUTABLE_PATH.toString()
    val mainScriptPath = Config.CODE_CHANGE_MINER_PATH.resolve("main.py").toString()
    val inputFilePath = inputFile.absolutePath
    val outputFilePath = Config.TEMP_DIRECTORY_PATH
        .resolve("pfg_${inputFile.nameWithoutExtension}.dot")
        .toString()
    val builder = ProcessBuilder().also {
        it.command(pythonExecPath, mainScriptPath, "pfg", "-i", inputFilePath, "-o", outputFilePath)
    }
    val process = builder.start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GraphBuildingException
    }
    val dotFile = File(outputFilePath)
    val dotPdfFile = File(dotFile.absolutePath.plus(".pdf"))
    dotFile.deleteOnExit()
    dotPdfFile.deleteOnExit()
    return dotFile
}