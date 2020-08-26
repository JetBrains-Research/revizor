package org.jetbrains.research.plugin.jgrapht.edges

/**
 * An edge comparator needed for the JGraphT `VF2SubgraphIsomorphismInspector`.
 */
class MultipleEdgeComparator : Comparator<PatternSpecificMultipleEdge> {

    private val edgeComparator: Comparator<PatternSpecificEdge?> =
        compareBy({ it?.xlabel }, { it?.fromClosure })

    override fun compare(fromTarget: PatternSpecificMultipleEdge?, fromPattern: PatternSpecificMultipleEdge?): Int {
        if (fromTarget?.embeddedEdgeByXlabel == null || fromPattern?.embeddedEdgeByXlabel == null)
            return 1
        for (xlabel in fromPattern.embeddedEdgeByXlabel.keys) {
            val comparisonResult = edgeComparator.compare(
                fromTarget.embeddedEdgeByXlabel[xlabel],
                fromPattern.embeddedEdgeByXlabel[xlabel]
            )
            if (comparisonResult != 0) return comparisonResult else continue
        }
        return 0
    }
}