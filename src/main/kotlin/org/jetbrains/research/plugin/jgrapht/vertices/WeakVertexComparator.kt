package org.jetbrains.research.plugin.jgrapht.vertices

import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex.LabelsGroup.Indicator.*

class WeakVertexComparator : Comparator<PatternSpecificVertex> {
    override fun compare(fromTarget: PatternSpecificVertex?, fromPattern: PatternSpecificVertex?): Int {
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            return when (fromPattern.dataNodeInfo?.whatMatters) {
                VALUABLE_ORIGINAL_LABEL ->
                    if (fromPattern.dataNodeInfo!!.labels.contains(fromTarget.originalLabel)) 0 else 1
                LONGEST_COMMON_SUFFIX ->
                    if (fromTarget.originalLabel!!.endsWith(fromPattern.dataNodeInfo!!.longestCommonSuffix)) 0 else 1
                NOTHING, null -> 0
            }
        }
        if (fromPattern?.originalLabel?.matches("""^.*?\[.*?\]$""".toRegex()) == true
            && fromTarget?.originalLabel?.matches("""^.*?\[.*?\]$""".toRegex()) == true
        ) {
            return 0
        }
        if (fromTarget?.originalLabel?.toLowerCase() == fromPattern?.originalLabel?.toLowerCase()
            && fromTarget?.label?.toLowerCase() == fromPattern?.label?.toLowerCase()
        ) {
            return 0
        }
        return 1
    }
}