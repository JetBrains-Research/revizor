package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.common.BugFinderConfig
import org.jetbrains.research.common.PatternsState

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            val currentMethodSrc: String = node.originalElement.text
            val tempFile = createTempFile(
                prefix = "method_${node.name}_",
                directory = BugFinderConfig.tempDirectory
            )
            tempFile.writeText(currentMethodSrc)
            tempFile.deleteOnExit()
            val dotGraphFile = buildPyFlowGraph(tempFile)
            val pfg = loadDAGFromDotFile(dotGraphFile)
            for (foundEntry in PatternsState.getIsomorphicPatterns(targetGraph = pfg)) {
                holder.registerProblem(
                    node.originalElement,
                    "Found relevant pattern: ${foundEntry.key}"
                )
            }
        }
    }
}