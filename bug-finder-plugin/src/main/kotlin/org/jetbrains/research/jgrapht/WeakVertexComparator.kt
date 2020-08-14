package org.jetbrains.research.jgrapht

class WeakVertexComparator : Comparator<PatternSpecificVertex> {
    override fun compare(fromTarget: PatternSpecificVertex?, fromPattern: PatternSpecificVertex?): Int {
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            val lcs = fromPattern.longestCommonSuffix ?: ""
            return if (fromTarget.originalLabel?.endsWith(lcs) == true) 0 else 1
        }
        if (fromTarget?.originalLabel?.toLowerCase() == fromPattern?.originalLabel?.toLowerCase()
            && fromTarget?.label?.toLowerCase() == fromPattern?.label?.toLowerCase()
        ) {
            return 0
        }
        return 1
    }
}