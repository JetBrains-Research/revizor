package org.jetbrains.research.jgrapht

class WeakVertexComparator : Comparator<PatternSpecificVertex> {
    override fun compare(fromTarget: PatternSpecificVertex?, fromPattern: PatternSpecificVertex?): Int {
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            return if (fromPattern.possibleVarLabels.size <= 3) {
                if (fromPattern.possibleVarLabels.contains(fromTarget.originalLabel)) 0 else 1
            } else {
                0
            }
        }
        if (fromTarget?.originalLabel?.toLowerCase() == fromPattern?.originalLabel?.toLowerCase()
            && fromTarget?.label?.toLowerCase() == fromPattern?.label?.toLowerCase()
        ) {
            return 0
        }
        return 1
    }
}