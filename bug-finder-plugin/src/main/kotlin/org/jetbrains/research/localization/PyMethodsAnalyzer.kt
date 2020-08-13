package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.PatternsStorage
import org.jetbrains.research.ide.BugFinderConfigService
import org.jetbrains.research.ide.PatternsSuggestions
import org.jetbrains.research.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.pyflowgraph.GraphBuildingException
import org.jetbrains.research.pyflowgraph.PyFlowGraphBuilder
import java.io.File

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {

    @ExperimentalStdlibApi
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodPyFlowGraph = PyFlowGraphBuilder().buildForPyFunction(node)
                val methodJGraph = PatternSpecificGraphsLoader.loadDAGFromPyFlowGraph(methodPyFlowGraph)
                val suggestions = PatternsStorage.getIsomorphicPatterns(targetGraph = methodJGraph).keys
                if (suggestions.isNotEmpty()) {
                    holder.registerProblem(
                        node.nameIdentifier ?: node,
                        "Found relevant patterns in method <${node.name}>",
                        ProblemHighlightType.WEAK_WARNING,
                        PatternsSuggestions(suggestions.toList())
                    )
                }
            } catch (exception: GraphBuildingException) {
                println("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }

    private fun createTempFileFromMethodSource(node: PyFunction): File {
        val configState = service<BugFinderConfigService>().state
        val currentMethodSrc: String = node.originalElement.text
        val tempFile = createTempFile(
            prefix = "method_${node.name}_",
            directory = configState.tempDirectory.toFile()
        )
        tempFile.writeText(currentMethodSrc)
        tempFile.deleteOnExit()
        return tempFile
    }

    private fun buildPyFlowGraphBySubprocess(inputFile: File): File {
        val configState = service<BugFinderConfigService>().state
        val pythonExecPath = configState.pythonExecutablePath.toString()
        val mainScriptPath = configState.codeChangeMinerPath.resolve("main.py").toString()
        val inputFilePath = inputFile.absolutePath
        val outputFilePath = configState.tempDirectory
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
}