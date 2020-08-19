package org.jetbrains.research.jgrapht

class MultipleEdgeComparator : Comparator<PatternSpecificMultipleEdge> {
    override fun compare(e0: PatternSpecificMultipleEdge?, e1: PatternSpecificMultipleEdge?): Int {
        if (e0?.embeddedEdgeByXlabel == null || e1?.embeddedEdgeByXlabel == null)
            return 1
        val edgeComparator = EdgeComparator()
        for (xlabel in e0.embeddedEdgeByXlabel.keys) {
            if (edgeComparator.compare(e0.embeddedEdgeByXlabel[xlabel], e1.embeddedEdgeByXlabel[xlabel]) != 0)
                return 1
        }
        return 0
    }
}

class EdgeComparator : Comparator<PatternSpecificEdge> {
    override fun compare(fromPattern: PatternSpecificEdge?, fromTarget: PatternSpecificEdge?): Int {
        if (fromPattern?.fromClosure != null)
            if (fromPattern.xlabel == fromTarget?.xlabel && fromPattern.fromClosure == fromTarget?.fromClosure)
                return 0
        else if (fromPattern.xlabel == fromTarget?.xlabel)
            return 0
        return 1
    }
}