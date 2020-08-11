package org.jetbrains.research.pyflowgraph

open class Edge(
    var label: String,
    var nodeFrom: Node,
    var nodeTo: Node,
    var fromClosure: Boolean = false
) {
    override fun toString(): String {
        return "$nodeFrom =$label> $nodeTo"
    }
}

class ControlEdge(nodeFrom: Node, nodeTo: Node, var branchKind: Boolean = true, fromClosure: Boolean = false) :
    Edge(LinkType.CONTROL, nodeFrom, nodeTo, fromClosure) {
    override fun toString(): String {
        return "$nodeFrom =$label> $nodeTo [$branchKind]"
    }
}

class DataEdge(label: String, nodeFrom: Node, nodeTo: Node, fromClosure: Boolean) :
    Edge(label, nodeFrom, nodeTo, fromClosure)

object LinkType {
    const val DEFINITION = "def"
    const val RECEIVER = "recv"
    const val REFERENCE = "ref"
    const val PARAMETER = "para"
    const val CONDITION = "cond"
    const val QUALIFIER = "qual"
    const val CONTROL = "control"
    const val DEPENDENCE = "dep"
}