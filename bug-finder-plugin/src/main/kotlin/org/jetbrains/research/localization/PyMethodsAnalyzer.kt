package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.PatternsStorage
import org.jetbrains.research.ide.BugFinderConfigService

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            val configState = service<BugFinderConfigService>().state
            val currentMethodSrc: String = node.originalElement.text
            val tempFile = createTempFile(
                prefix = "method_${node.name}_",
                directory = configState.tempDirectory.toFile()
            )
            tempFile.writeText(currentMethodSrc)
            tempFile.deleteOnExit()
            try {
                val dotGraphFile = buildPyFlowGraph(tempFile)
                val pfg = loadDAGFromDotFile(dotGraphFile)
                for (foundEntry in PatternsStorage.getIsomorphicPatterns(targetGraph = pfg)) {
                    holder.registerProblem(
                        node.originalElement,
                        "Found relevant pattern: ${foundEntry.key}"
                    )
                }
            } catch (exception: UnableToBuildPyFlowGraphException) {
                println("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}