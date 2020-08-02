package org.jetbrains.research.preprocessing

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.common.Config
import org.jetbrains.research.common.createAndSavePyFlowGraph
import org.jetbrains.research.common.loadGraphFromDotFile
import java.io.File

class PyMethodExtractor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            val currentMethodSrc: String = node.originalElement.text
            val tempFile = createTempFile(
                prefix = "method_${node.name}_",
                directory = Config.tempDirectory
            )
            tempFile.writeText(currentMethodSrc)
            tempFile.deleteOnExit()
            val dotGraphFile = createAndSavePyFlowGraph(tempFile)
            val pfg = loadGraphFromDotFile(dotGraphFile)
            println(pfg)
        }
    }
}