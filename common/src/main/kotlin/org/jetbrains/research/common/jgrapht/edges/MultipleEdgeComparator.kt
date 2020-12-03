package org.jetbrains.research.common.jgrapht.edges

/**
 * An edge comparator needed for the JGraphT `VF2SubgraphIsomorphismInspector`.
 */
class MultipleEdgeComparator : Comparator<PatternSpecificMultipleEdge> {

    private val edgeComparator: Comparator<PatternSpecificEdge?> =
        compareBy { it?.xlabel }

    override fun compare(fromTarget: PatternSpecificMultipleEdge?, fromPattern: PatternSpecificMultipleEdge?): Int {
        if (fromTarget?.embeddedEdgeByXlabel == null || fromPattern?.embeddedEdgeByXlabel == null)
            return 1
        for (xlabel in fromPattern.embeddedEdgeByXlabel.keys) {
            val edgeFromTarget = fromTarget.embeddedEdgeByXlabel[xlabel]
            val edgeFromPattern = fromPattern.embeddedEdgeByXlabel[xlabel]
            val comparisonResult = edgeComparator.compare(edgeFromTarget, edgeFromPattern)
            if (comparisonResult != 0) return comparisonResult else continue
        }
        return 0
    }
}