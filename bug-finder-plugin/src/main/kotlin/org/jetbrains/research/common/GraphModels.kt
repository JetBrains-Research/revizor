package org.jetbrains.research.common

import org.jgrapht.graph.DefaultEdge

data class Vertex(
    val id: String,
    val label: String?,
    val originalLabel: String?,
    val color: String?,
    val shape: String?,
    var longestCommonSuffix: String? = null
)

data class MultipleEdge(
    val id: DefaultEdge,
    val embeddedEdgeByXlabel: MutableMap<String?, Edge>
)

data class Edge(
    val id: DefaultEdge,
    val xlabel: String?,
    val fromClosure: Boolean?,
    val style: String?
)

class PatternVertexComparator : Comparator<Vertex> {
    override fun compare(fromTarget: Vertex?, fromPattern: Vertex?): Int {
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            val lcs = fromPattern.longestCommonSuffix ?: ""
            return if (fromTarget.originalLabel?.endsWith(lcs) == true) 0 else 1
        }
        if (fromTarget?.originalLabel == fromPattern?.originalLabel && fromTarget?.label == fromPattern?.label)
            return 0
        return 1
    }
}

class MultipleEdgeComparator : Comparator<MultipleEdge> {
    override fun compare(e0: MultipleEdge?, e1: MultipleEdge?): Int {
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

class EdgeComparator : Comparator<Edge> {
    override fun compare(e0: Edge?, e1: Edge?): Int {
        if (e0?.xlabel == e1?.xlabel && e0?.fromClosure == e1?.fromClosure)
            return 0
        return 1
    }
}

