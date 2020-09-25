package org.jetbrains.research.plugin.localization

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
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

    class PatternBasedProblemsHolder {
        val elementsByPatternId: MutableMap<String, MutableList<PyElement>> = hashMapOf()
        val patternsIdsByElement: MutableMap<PyElement, MutableSet<String>> = hashMapOf()
    }

    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            try {
                val methodGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                val patternsMappings = PatternsStorage.getIsomorphicPatterns(targetGraph = methodGraph)
                val problems = PatternBasedProblemsHolder()
                for ((patternId, mappings) in patternsMappings) {
                    val patternGraph = PatternsStorage.getPatternById(patternId)!!
                    for (mapping in mappings) {
                        for (patternVertex in patternGraph.vertexSet()) {
                            val targetVertex = mapping.getVertexCorrespondence(patternVertex, false)
                            val problematicToken = targetVertex.origin?.psi
                            if (problematicToken != null) {
                                problems.elementsByPatternId
                                    .getOrPut(patternId) { arrayListOf() }
                                    .add(problematicToken)
                                problems.patternsIdsByElement
                                    .getOrPut(problematicToken) { hashSetOf() }
                                    .add(patternId)
                            }
                        }
                    }
                }
                for (token in problems.patternsIdsByElement.keys) {
                    holder.registerProblem(
                        token,
                        "Found relevant patterns in method <${node.name}>",
                        ProblemHighlightType.WARNING,
                        PatternBasedAutoFix(token, problems)
                    )
                }
            } catch (exception: GraphBuildingException) {
                val logger = Logger.getInstance(this::class.java)
                logger.warn("Unable to build PyFlowGraph for method <${node.name}>")
            }
        }
    }
}