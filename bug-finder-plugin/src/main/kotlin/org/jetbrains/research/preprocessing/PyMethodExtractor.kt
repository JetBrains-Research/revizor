package org.jetbrains.research.preprocessing

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.common.*
import org.jgrapht.alg.isomorphism.VF2SubgraphIsomorphismInspector

class PyMethodExtractor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyFunction(node: PyFunction?) {
        if (node != null) {
            val currentMethodSrc: String = node.originalElement.text
            val tempFile = createTempFile(
                prefix = "method_${node.name}_",
                directory = Config.tempDirectory
            )
            tempFile.writeText(currentMethodSrc)
            tempFile.deleteOnExit()
            val dotGraphFile = createAndSavePyFlowGraph(tempFile)
            val pfg = loadGraphFromDotFile(dotGraphFile)
            for (entry in PatternsState.patternsGraphs) {
                val patternPath = entry.key
                val patternGraph = entry.value
                val isomorphismInspector = VF2SubgraphIsomorphismInspector<Vertex, MultipleEdge>(
                    pfg, patternGraph, VertexComparator(), MultipleEdgeComparator(), false
                )
                if (isomorphismInspector.isomorphismExists()) {
                    holder.registerProblem(
                        node.originalElement,
                        "Found relevant pattern: $patternPath"
                    )
                }
            }
        }
    }
}