package org.jetbrains.research.plugin.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.ide.PatternsSuggestions
import org.jetbrains.research.plugin.pyflowgraph.GraphBuildingException

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {

    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodJGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                val patternsMappings = PatternsStorage.getIsomorphicPatterns(targetGraph = methodJGraph)
                val patternsIdsByProblematicToken = HashMap<PsiElement, ArrayList<String>>()
                for ((patternId, mapping) in patternsMappings) {
                    val patternGraph = PatternsStorage.getPatternById(patternId)!!
                    for (vertex in patternGraph.vertexSet()) {
                        val mappedTargetVertex = mapping.getVertexCorrespondence(vertex, false)
                        if (mappedTargetVertex.origin?.psi != null) {
                            val problematicToken = mappedTargetVertex.origin!!.psi!!.originalElement
                            patternsIdsByProblematicToken.getOrPut(problematicToken) { arrayListOf() }.add(patternId)
                        }
                    }
                }
                for ((token, patternsIds) in patternsIdsByProblematicToken) {
                    holder.registerProblem(
                        token,
                        "Found relevant patterns in method <${node.name}>",
                        ProblemHighlightType.WARNING,
                        PatternsSuggestions(patternsIds)
                    )
                }
            } catch (exception: GraphBuildingException) {
                println("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}