package org.jetbrains.research.plugin.jgrapht

class StrictVertexComparator : Comparator<PatternSpecificVertex> {
    override fun compare(fromTarget: PatternSpecificVertex?, fromPattern: PatternSpecificVertex?): Int {
        if (fromTarget?.originalLabel?.toLowerCase() == fromPattern?.originalLabel?.toLowerCase()
            && fromTarget?.label?.toLowerCase() == fromPattern?.label?.toLowerCase()
        ) {
            return 0
        }
        return 1
    }
}