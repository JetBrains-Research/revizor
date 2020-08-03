package org.jetbrains.research.common

import org.jgrapht.graph.DefaultEdge


data class Vertex(
    val id: String,
    val label: String?,
    val originalLabel: String?,
    val color: String?,
    val shape: String?
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

class VertexComparator : Comparator<Vertex> {
    override fun compare(u: Vertex?, v: Vertex?): Int {
        if (u?.label?.startsWith("var") == true && v?.label?.startsWith("var") == true)
            return 0
        if (u?.originalLabel == v?.originalLabel && u?.label == v?.label)
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

