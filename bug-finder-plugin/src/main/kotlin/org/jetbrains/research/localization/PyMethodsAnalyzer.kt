package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.PatternsStorage
import org.jetbrains.research.ide.BugFinderConfigService
import org.jetbrains.research.ide.PatternsSuggestions

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
                val suggestions = PatternsStorage.getIsomorphicPatterns(targetGraph = pfg).keys.toList()
                if (suggestions.isNotEmpty()) {
                    holder.registerProblem(
                        node.nameIdentifier ?: node,
                        "Found relevant patterns in method <${node.name}>",
                        ProblemHighlightType.WEAK_WARNING,
                        PatternsSuggestions(suggestions)
                    )
                }
            } catch (exception: UnableToBuildPyFlowGraphException) {
                println("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}