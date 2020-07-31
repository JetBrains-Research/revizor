package org.jetbrains.research.preprocessing

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.BugFinderConfig
import java.io.File

class PyMethodExtractor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            val currentMethodSrc: String = node.originalElement.text
            val tempFile = createTempFile(
                prefix = "method_${node.name}",
                directory = BugFinderConfig.tempDirectory
            )
            tempFile.writeText(currentMethodSrc)
            createAndSavePyFlowGraphs(tempFile)
            tempFile.deleteOnExit()
        }
    }

    private fun createAndSavePyFlowGraphs(inputFile: File) {
        val pythonExecPath = BugFinderConfig.pythonExecutablePath.toString()
        val mainScriptPath = BugFinderConfig.codeChangeMinerPath.resolve("main.py").toString()
        val inputFilePath = inputFile.absolutePath
        val outputFilePath = BugFinderConfig.tempDirectory.toPath()
            .resolve("pfg_${inputFile.nameWithoutExtension}.dot")
            .toString()
        val builder = ProcessBuilder().also {
            it.command(pythonExecPath, mainScriptPath, "pfg", "-i", inputFilePath, "-o", outputFilePath)
        }
        builder.start().also { it.waitFor() }

    }
}