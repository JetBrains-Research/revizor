package org.jetbrains.research.common.jgrapht.vertices

import kotlinx.serialization.Serializable
import org.jetbrains.research.common.pyflowgraph.models.DataNode
import org.jetbrains.research.common.pyflowgraph.models.Node

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
    var metadata: String = "",
    var kind: String? = null
) {
    enum class ChangeGraphPartIndicator { BEFORE, AFTER }

    enum class MatchingMode {
        LONGEST_COMMON_SUFFIX,
        VALUABLE_ORIGINAL_LABEL,
        NOTHING,
        UNKNOWN
    }

    @Serializable
    data class LabelsGroup(
        val whatMatters: MatchingMode,
        val labels: HashSet<String>,
        val longestCommonSuffix: String
    ) {
        companion object {
            fun getEmpty() = LabelsGroup(MatchingMode.UNKNOWN, hashSetOf(), "")
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