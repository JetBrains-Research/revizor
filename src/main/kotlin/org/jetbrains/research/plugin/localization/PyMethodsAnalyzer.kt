package org.jetbrains.research.plugin.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.ide.PatternBasedAutoFix
import org.jetbrains.research.plugin.pyflowgraph.GraphBuildingException

/**
 * A class for running inspection on particular methods.
 *
 * This class provides Python PSI visitor which tries to find isomorphic patterns
 * for each PyFunction element in the code.
 */
class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {

    data class ProblemDescription(
        val patternId: String,
        val patternPsiElement: PyElement
    )

    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                val patternsMappings = PatternsStorage.getIsomorphicPatterns(targetGraph = methodGraph)
                val registeredProblemsByProblematicToken = HashMap<PsiElement, HashSet<ProblemDescription>>()
                for ((patternId, mappings) in patternsMappings) {
                    val patternGraph = PatternsStorage.getPatternById(patternId)!!
                    for (mapping in mappings) {
                        for (patternVertex in patternGraph.vertexSet()) {
                            val targetVertex = mapping.getVertexCorrespondence(patternVertex, false)
                            if (targetVertex.origin?.psi != null) {
                                val problematicToken = targetVertex.origin!!.psi!!.originalElement
                                val patternPsiElement = PatternsStorage
                                    .getPatternPsiElementByIdAndVertex(patternId, patternVertex)
                                if (patternPsiElement != null) {
                                    registeredProblemsByProblematicToken
                                        .getOrPut(problematicToken) { hashSetOf() }
                                        .add(ProblemDescription(patternId, patternPsiElement))
                                }
                            }
                        }
                    }
                }
                for ((token, problems) in registeredProblemsByProblematicToken) {
                    for (problem in problems) {
                        holder.registerProblem(
                            token,
                            "Found relevant patterns in method <${node.name}>",
                            ProblemHighlightType.WARNING,
                            PatternBasedAutoFix(
                                problem.patternId,
                                SmartPointerManager.createPointer(problem.patternPsiElement)
                            )
                        )
                    }
                }
            } catch (exception: GraphBuildingException) {
                val logger = Logger.getInstance(this::class.java)
                logger.warn("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}