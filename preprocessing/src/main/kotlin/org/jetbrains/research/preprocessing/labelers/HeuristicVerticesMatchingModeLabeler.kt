package org.jetbrains.research.preprocessing.labelers

import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.preprocessing.getLongestCommonSuffix

class HeuristicVerticesMatchingModeLabeler(
    override val reprFragment: PatternGraph,
    override val allFragments: List<PatternGraph>
) : VerticesMatchingModeLabeler {

    override fun markVertices(): Map<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup> {
        val reprVertexToAllLabels: Map<PatternSpecificVertex, MutableSet<String>> = extractLabelsForEachVertex()
        val reprVertexToLabelsGroup = hashMapOf<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup>()

        for (reprVertex in reprFragment.vertexSet()) {
            if (reprVertex.label?.startsWith("var") == true) {
                val labels = reprVertexToAllLabels.getOrDefault(reprVertex, hashSetOf())
                val lcs = getLongestCommonSuffix(labels)
                when {
                    lcs.contains('.') -> {
                        // If all the labels have common attribute at the end, such as `.size`
                        reprVertexToLabelsGroup[reprVertex] = PatternSpecificVertex.LabelsGroup(
                            whatMatters = PatternSpecificVertex.MatchingMode.LONGEST_COMMON_SUFFIX,
                            labels = labels as HashSet<String>,
                            longestCommonSuffix = ".${lcs.substringAfter('.')}"
                        )
                    }
                    labels.all { it.contains('.') } -> {
                        // If the labels do not have any common attribute suffix, but still have some attributes,
                        // we should save their original labels as well
                        reprVertexToLabelsGroup[reprVertex] = PatternSpecificVertex.LabelsGroup(
                            whatMatters = PatternSpecificVertex.MatchingMode.VALUABLE_ORIGINAL_LABEL,
                            labels = labels as HashSet<String>,
                            longestCommonSuffix = ""
                        )
                    }
                    else -> {
                        // Otherwise, just suppose that this vertex corresponds to a variable,
                        // so its name does not matter
                        reprVertexToLabelsGroup[reprVertex] = PatternSpecificVertex.LabelsGroup(
                            whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                            labels = labels as HashSet<String>,
                            longestCommonSuffix = ""
                        )
                    }
                }
            }
        }

        return reprVertexToLabelsGroup
    }
}


