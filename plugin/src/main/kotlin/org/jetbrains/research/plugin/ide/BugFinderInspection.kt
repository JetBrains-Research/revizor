package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.common.buildPyFlowGraphForMethod
import org.jetbrains.research.common.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.PatternsStorage
import org.jgrapht.GraphMapping

/**
 * A main class for running inspection on methods.
 *
 * This class includes PsiElementVisitor which tries to find isomorphic patterns
 * for each PyFunction element in the code.
 */
class BugFinderInspection : LocalInspectionTool() {

    private val logger = Logger.getInstance(this::class.java)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return PyMethodsAnalyzer(holder)
    }

    inner class PyMethodsAnalyzer(private val holder: ProblemsHolder) : PyElementVisitor() {
        override fun visitPyFunction(node: PyFunction?) {
            if (node != null) {
                try {
                    val documentManager = PsiDocumentManager.getInstance(holder.project)
                    val methodGraph = buildPyFlowGraphForMethod(node, builder = "kotlin")
                    val patternsMappings =
                        PatternsStorage.getIsomorphicPatterns(targetDirectedAcyclicGraph = methodGraph)
                    val mappingsHolder = DetectedVertexMappingsHolder()
                    val tokenByLineByPattern = hashMapOf<String, HashMap<Int, PsiElement>>()
                    val vertexByHighlightedToken = hashMapOf<PsiElement, PatternSpecificVertex>()
                    for ((patternId, mappings) in patternsMappings) {
                        val patternGraph = PatternsStorage.getPatternById(patternId)!!
                        for (mapping in mappings) {
                            val patternToTargetVarNamesMapping = hashMapOf<String, String>()
                            for (patternVertex in patternGraph.vertexSet()) {
                                val targetVertex = mapping.getVertexCorrespondence(patternVertex, false)
                                targetVertex.metadata = patternVertex.metadata // FIXME: vulnerable
                                if (patternVertex.label?.startsWith("var") == true
                                    && patternVertex.originalLabel != null
                                    && targetVertex.originalLabel != null
                                ) {
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
                                mappingsHolder.verticesByPatternId
                                    .getOrPut(patternId) { arrayListOf() }
                                    .add(targetVertex)
                                mappingsHolder.patternsIdsByVertex
                                    .getOrPut(targetVertex) { hashSetOf() }
                                    .add(patternId)
                                mappingsHolder.vertexMappingsByTargetVertex
                                    .getOrPut(targetVertex) { hashMapOf() }[patternId] = mapping

                                // Find a Least Common Ancestor for all tokens by each pattern per line to highlight
                                if (targetVertex.metadata == "hanger")
                                    continue
                                var targetToken = targetVertex.origin?.psi!! as PsiElement?
                                val line = documentManager.getDocument(targetToken!!.containingFile)
                                    ?.getLineNumber(targetToken.textOffset)!!
                                val tokenByLine = tokenByLineByPattern.getOrPut(patternId) { hashMapOf() }
                                if (tokenByLine.containsKey(line)) {
                                    var prevLCAToken = tokenByLine[line]
                                    val visited = hashSetOf<PsiElement>()
                                    while (targetToken != null && prevLCAToken != null) {
                                        if (visited.contains(targetToken)) {
                                            break
                                        }
                                        visited.add(targetToken)
                                        if (visited.contains(prevLCAToken)) {
                                            targetToken = prevLCAToken
                                            break
                                        }
                                        visited.add(prevLCAToken)
                                        prevLCAToken = prevLCAToken.parent
                                        targetToken = targetToken.parent
                                    }
                                }
                                tokenByLine[line] = targetToken!!
                                if (targetVertex.origin?.psi!! == targetToken) {
                                    vertexByHighlightedToken[targetToken] = targetVertex
                                }
                            }
                            mappingsHolder.varNamesMappingByVertexMapping[mapping] = patternToTargetVarNamesMapping
                        }
                    }
                    for (tokenByLine in tokenByLineByPattern.values) {
                        for (token in tokenByLine.values) {
                            val problematicVertex = vertexByHighlightedToken[token]!!
                            if (problematicVertex.metadata == "hanger")
                                continue
                            holder.registerProblem(
                                token,
                                "Found relevant pattern in method `${node.name}`",
                                ProblemHighlightType.WARNING,
                                PatternBasedAutoFix(problematicVertex, mappingsHolder)
                            )
                        }
                    }
                } catch (exception: Exception) {
                    logger.warn("Unable to build PyFlowGraph for method `${node.name}`")
                }
            }
        }

        inner class DetectedVertexMappingsHolder {
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