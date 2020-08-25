package org.jetbrains.research.plugin.jgrapht.vertices

import org.jetbrains.research.plugin.pyflowgraph.models.DataNode
import org.jetbrains.research.plugin.pyflowgraph.models.Node

data class PatternSpecificVertex(
    val id: String,
    var label: String? = null,
    var originalLabel: String? = null,
    var fromPart: ChangeGraphPartIndicator? = null,
    var color: String? = null,
    var shape: String? = null,
    var dataNodeInfo: LabelsGroup? = null,
    var origin: Node? = null
) {

    enum class ChangeGraphPartIndicator { BEFORE, AFTER }

    data class LabelsGroup(
        val whatMatters: Indicator,
        val labels: HashSet<String>,
        val longestCommonSuffix: String
    ) {
        enum class Indicator {
            LONGEST_COMMON_SUFFIX,
            VALUABLE_ORIGINAL_LABEL,
            NOTHING,
            UNKNOWN
        }
    }

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
        this.fromPart = ChangeGraphPartIndicator.BEFORE
        this.origin = pfgNode
    }
}