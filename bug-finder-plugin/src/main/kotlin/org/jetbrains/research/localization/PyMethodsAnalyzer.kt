package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.PatternsStorage
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.ide.PatternsSuggestions
import org.jetbrains.research.pyflowgraph.GraphBuildingException

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {

    @ExperimentalStdlibApi
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodJGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
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
}