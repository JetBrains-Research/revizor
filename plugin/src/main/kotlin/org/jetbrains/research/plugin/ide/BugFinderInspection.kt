package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.buildPyFlowGraphForMethod
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
                    val patternsMappings =
                        PatternsStorage.getIsomorphicPatterns(targetDirectedAcyclicGraph = methodGraph)
                    val problems = DetectedVertexMappingsHolder()
                    for ((patternId, mappings) in patternsMappings) {
                        val patternGraph = PatternsStorage.getPatternById(patternId)!!
                        for (mapping in mappings) {
                            val patternToTargetVarNamesMapping = hashMapOf<String, String>()
                            for (patternVertex in patternGraph.vertexSet()) {
                                val targetVertex = mapping.getVertexCorrespondence(patternVertex, false)
                                targetVertex.metadata = patternVertex.metadata // FIXME: vulnerable
                                if (patternVertex.label?.startsWith("var") == true
                                        && patternVertex.originalLabel != null
                                        && targetVertex.originalLabel != null) {
                                    // Smart saving of variable names mapping, even in case "name1.attr -> name2.attr"
                                    val s1 = patternVertex.originalLabel!!
                                    val s2 = targetVertex.originalLabel!!
                                    val possiblePatternAttrs = s1.split(".").reversed()
                                    val possibleTargetAttrs = s2.split(".").reversed()
                                    for ((attr1, attr2) in possiblePatternAttrs.zip(possibleTargetAttrs)) {
                                        if (attr1 != attr2) {
                                            patternToTargetVarNamesMapping[attr1] = attr2
                                        }
                                    }
                                    patternToTargetVarNamesMapping[s1] = s2
                                }
                                problems.verticesByPatternId
                                        .getOrPut(patternId) { arrayListOf() }
                                        .add(targetVertex)
                                problems.patternsIdsByVertex
                                        .getOrPut(targetVertex) { hashSetOf() }
                                        .add(patternId)
                                problems.vertexMappingsByTargetVertex
                                        .getOrPut(targetVertex) { hashMapOf() }
                                        .put(patternId, mapping)
                            }
                            problems.varNamesMappingByVertexMapping[mapping] = patternToTargetVarNamesMapping
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

        class DetectedVertexMappingsHolder {
            val verticesByPatternId: MutableMap<String, MutableList<PatternSpecificVertex>> = hashMapOf()
            val patternsIdsByVertex: MutableMap<PatternSpecificVertex, MutableSet<String>> = hashMapOf()
            val vertexMappingsByTargetVertex =
                    hashMapOf<PatternSpecificVertex,
                            HashMap<String, GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()
            val varNamesMappingByVertexMapping =
                    hashMapOf<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>,
                            HashMap<String, String>>()
        }
    }

}