package org.jetbrains.research.preprocessing.labelers

import org.jetbrains.research.common.PatternGraph
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.preprocessing.getLongestCommonSuffix

class ManualVerticesMatchingModeLabeler(
    override val reprFragment: PatternGraph,
    override val allFragments: List<PatternGraph>
) : VerticesMatchingModeLabeler {

    override fun markVertices(): Map<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup> {
        val reprVertexToAllLabels: Map<PatternSpecificVertex, MutableSet<String>> = extractLabelsForEachVertex()
        val reprVertexToLabelsGroup = hashMapOf<PatternSpecificVertex, PatternSpecificVertex.LabelsGroup>()

        println("Start marking")

        for (reprVertex in reprFragment.vertexSet()) {
            if (reprVertex.label?.startsWith("var") == true) {
                val labels = reprVertexToAllLabels.getOrDefault(reprVertex, hashSetOf())
                println("Labels of the current vertex $reprVertex:")
                println(labels)
                var exit = false
                while (!exit) {
                    println("Choose, what will be considered as main factor when matching nodes (labels/lcs/nothing):")
                    val ans = readLine()
                    println("Your answer: $ans")
                    when (ans) {
                        "labels" -> reprVertexToLabelsGroup[reprVertex] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.VALUABLE_ORIGINAL_LABEL,
                                labels = reprVertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            ).also { exit = true }
                        "lcs" -> reprVertexToLabelsGroup[reprVertex] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.LONGEST_COMMON_SUFFIX,
                                labels = reprVertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = getLongestCommonSuffix(reprVertex.dataNodeInfo?.labels)
                            ).also { exit = true }
                        "nothing" -> reprVertexToLabelsGroup[reprVertex] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                                labels = reprVertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            ).also { exit = true }
                    }
                }
            }
        }

        println("Finish marking")
        return reprVertexToLabelsGroup
    }
}