package org.jetbrains.research.preprocessing.labelers

import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.jgrapht.getSuperWeakSubgraphIsomorphismInspector
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex

interface VerticesMatchingModeLabeler {
    val reprFragment: PatternGraph
    val allFragments: List<PatternGraph>

    fun markVertices(): Map<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup>

    fun extractLabelsForEachVertex(): Map<PatternSpecificVertex, MutableSet<String>> {
        val reprVertexToAllLabels = hashMapOf<PatternSpecificVertex, MutableSet<String>>()
        for (curFragment in allFragments) {
            val inspector = getSuperWeakSubgraphIsomorphismInspector(reprFragment, curFragment)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().first()
                for (curVertex in curFragment.vertexSet()) {
                    if (curVertex.label?.startsWith("var") == true) {
                        val reprVertex = mapping.getVertexCorrespondence(curVertex, false)
                        reprVertexToAllLabels.getOrPut(reprVertex) { hashSetOf() }.add(curVertex.originalLabel!!)
                    }
                }
            } else {
                throw IllegalStateException("Fragments are not isomorphic")
            }
        }
        return reprVertexToAllLabels
    }
}
