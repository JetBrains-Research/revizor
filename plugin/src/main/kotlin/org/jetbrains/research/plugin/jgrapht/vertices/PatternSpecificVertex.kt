package org.jetbrains.research.plugin.jgrapht.vertices

import org.jetbrains.research.plugin.pyflowgraph.models.DataNode
import org.jetbrains.research.plugin.pyflowgraph.models.Node

/**
 * A vertex needed for the JGraphT interlayer.
 */
data class PatternSpecificVertex(
    var id: Int,
    var label: String? = null,
    var originalLabel: String? = null,
    var fromPart: ChangeGraphPartIndicator? = null,
    var color: String? = null,
    var shape: String? = null,
    var dataNodeInfo: LabelsGroup? = null,
    var origin: Node? = null,
    var metadata: String = ""
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

    constructor(pfgNode: Node) : this(pfgNode.statementNum) {
        this.label = pfgNode.label
        if (pfgNode is DataNode) {
            val subKind = pfgNode.kind
            if (subKind == DataNode.Kind.VARIABLE_DECLARATION || subKind == DataNode.Kind.VARIABLE_USAGE) {
                this.label = CommonLabel.VARIABLE
            } else if (subKind == DataNode.Kind.LITERAL || subKind == DataNode.Kind.KEYWORD) {
                this.label = CommonLabel.LITERAL
            }
        }
        this.originalLabel = pfgNode.label
        this.fromPart = ChangeGraphPartIndicator.BEFORE
        this.origin = pfgNode
    }
}