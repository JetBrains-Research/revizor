package org.jetbrains.research.jgrapht

import org.jetbrains.research.pyflowgraph.models.DataNode
import org.jetbrains.research.pyflowgraph.models.Node

data class PatternSpecificVertex(
    val id: String,
    var label: String? = null,
    var originalLabel: String? = null,
    var color: String? = null,
    var shape: String? = null,
    var longestCommonSuffix: String? = null
) {

    object CommonLabel {
        const val VARIABLE = "var"
        const val LITERAL = "lit"
    }

    object SubKind {
        const val DATA_VARIABLE_DECLARATION = DataNode.Kind.VARIABLE_DECLARATION
        const val DATA_VARIABLE_USAGE = DataNode.Kind.VARIABLE_USAGE
        const val DATA_LITERAL = DataNode.Kind.LITERAL
        const val DATA_KEYWORD = DataNode.Kind.KEYWORD
    }

    constructor(pfgNode: Node) : this(pfgNode.statementNum.toString()) {
        this.label = pfgNode.label
        if (pfgNode is DataNode) {
            val subKind = pfgNode.kind
            if (subKind == SubKind.DATA_VARIABLE_DECLARATION || subKind == SubKind.DATA_VARIABLE_USAGE) {
                this.label = CommonLabel.VARIABLE
            } else if (subKind == SubKind.DATA_LITERAL || subKind == SubKind.DATA_KEYWORD) {
                this.label = CommonLabel.LITERAL
            }
        }
        this.originalLabel = pfgNode.label
        this.color = "red2"
    }
}

class PatternVertexComparator : Comparator<PatternSpecificVertex> {
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