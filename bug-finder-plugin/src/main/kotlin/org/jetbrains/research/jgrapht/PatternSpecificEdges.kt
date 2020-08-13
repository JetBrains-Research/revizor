package org.jetbrains.research.jgrapht

data class PatternSpecificMultipleEdge(
    val embeddedEdgeByXlabel: MutableMap<String?, PatternSpecificEdge>
)

data class PatternSpecificEdge(
    val xlabel: String? = null,
    val fromClosure: Boolean? = null,
    val style: String? = null
)

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
    override fun compare(e0: PatternSpecificEdge?, e1: PatternSpecificEdge?): Int {
        if (e0?.xlabel == e1?.xlabel && e0?.fromClosure == e1?.fromClosure)
            return 0
        return 1
    }
}

