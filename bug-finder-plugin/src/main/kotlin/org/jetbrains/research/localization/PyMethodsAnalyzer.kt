package org.jetbrains.research.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.PatternsStorage
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.ide.PatternsSuggestions
import org.jetbrains.research.pyflowgraph.GraphBuildingException
import java.nio.file.Path

class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {


    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodJGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                val patternsMappings = PatternsStorage.getIsomorphicPatterns(targetGraph = methodJGraph)
                val patternPathByProblematicToken = HashMap<PsiElement, ArrayList<Path>>()
                for ((path, mapping) in patternsMappings) {
                    val patternGraph = PatternsStorage.getPatternGraphByPath(path)!!
                    for (vertex in patternGraph.vertexSet()) {
                        val mappedTargetVertex = mapping.getVertexCorrespondence(vertex, false)
                        if (mappedTargetVertex.origin?.psi != null) {
                            val problematicToken = mappedTargetVertex.origin!!.psi!!.originalElement
                            patternPathByProblematicToken.getOrPut(problematicToken) { arrayListOf() }.add(path)
                        }
                    }
                }
                for ((token, patternsPaths) in patternPathByProblematicToken) {
                    holder.registerProblem(
                        token,
                        "Found relevant patterns in method <${node.name}>",
                        ProblemHighlightType.WARNING,
                        PatternsSuggestions(patternsPaths)
                    )
                }
                print(holder.resultCount)
            } catch (exception: GraphBuildingException) {
                println("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}