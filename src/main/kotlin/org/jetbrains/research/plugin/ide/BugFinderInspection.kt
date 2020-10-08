package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.pyflowgraph.GraphBuildingException
import org.jgrapht.GraphMapping

/**
 * A main class for running inspection on methods.
 *
 * This class includes PsiElementVisitor which tries to find isomorphic patterns
 * for each PyFunction element in the code.
 */
class BugFinderInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return PyMethodsAnalyzer(holder)
    }

    class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {
        override fun visitPyFunction(node: PyFunction?) {
            if (node != null) {
                try {
                    val methodGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                    val patternsMappings = PatternsStorage.getIsomorphicPatterns(targetGraph = methodGraph)
                    val problems = FoundProblemsHolder()
                    for ((patternId, mappings) in patternsMappings) {
                        val patternGraph = PatternsStorage.getPatternById(patternId)!!
                        for (mapping in mappings) {
                            for (patternVertex in patternGraph.vertexSet()) {
                                val targetVertex = mapping.getVertexCorrespondence(patternVertex, false)
                                targetVertex.metadata = patternVertex.metadata // FIXME: vulnerable
                                problems.verticesByPatternId
                                    .getOrPut(patternId) { arrayListOf() }
                                    .add(targetVertex)
                                problems.patternsIdsByVertex
                                    .getOrPut(targetVertex) { hashSetOf() }
                                    .add(patternId)
                                problems.mappingByVertex[targetVertex] = mapping
                            }
                        }
                    }
                    for (problematicVertex in problems.patternsIdsByVertex.keys) {
                        if (problematicVertex.metadata == "hanger")
                            continue
                        holder.registerProblem(
                            problematicVertex.origin?.psi!!,
                            "Found relevant patterns in method <${node.name}>",
                            ProblemHighlightType.WARNING,
                            PatternBasedAutoFix(problematicVertex, problems)
                        )
                    }
                } catch (exception: GraphBuildingException) {
                    val logger = Logger.getInstance(this::class.java)
                    logger.warn("Unable to build PyFlowGraph for method <${node.name}>")
                }
            }
        }

        class FoundProblemsHolder {
            val verticesByPatternId: MutableMap<String, MutableList<PatternSpecificVertex>> = hashMapOf()
            val patternsIdsByVertex: MutableMap<PatternSpecificVertex, MutableSet<String>> = hashMapOf()
            val mappingByVertex =
                hashMapOf<PatternSpecificVertex, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
        }
    }

}