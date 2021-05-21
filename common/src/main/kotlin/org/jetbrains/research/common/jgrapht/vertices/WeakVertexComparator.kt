package org.jetbrains.research.common.jgrapht.vertices

import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex.MatchingMode.*
import org.jetbrains.research.common.pyflowgraph.models.DataNode

/**
 * A vertices comparator needed for the JGraphT `VF2SubgraphIsomorphismInspector`.
 */
class WeakVertexComparator : Comparator<PatternSpecificVertex> {

    override fun compare(fromTarget: PatternSpecificVertex?, fromPattern: PatternSpecificVertex?): Int {

        // Smart check for the variable names correspondence
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            // FIXME: Differ <variable-usage> and <variable-decl> nodes in the original miner
            // Now it is temporary solution
            val patternVarRoleLabel = fromPattern.kind
            val targetVarRoleLabel = (fromTarget.origin as? DataNode?)?.kind
            if (patternVarRoleLabel != null && targetVarRoleLabel != null && patternVarRoleLabel != targetVarRoleLabel) {
                return 1
            }
            return when (fromPattern.dataNodeInfo?.whatMatters) {
                VALUABLE_ORIGINAL_LABEL ->
                    if (fromPattern.dataNodeInfo!!.labels.contains(fromTarget.originalLabel)) 0 else 1
                LONGEST_COMMON_SUFFIX ->
                    if (fromTarget.originalLabel!!.endsWith(fromPattern.dataNodeInfo!!.longestCommonSuffix)) 0 else 1
                NOTHING, UNKNOWN, null -> 0
            }
        }

        // PySubscriptionExpressions always match
        if (fromPattern?.originalLabel?.matches("""^.*?\[.*?\]$""".toRegex()) == true
            && fromTarget?.originalLabel?.matches("""^.*?\[.*?\]$""".toRegex()) == true
        ) {
            return 0
        }

        // Smart check for literals
        if (fromPattern?.label?.startsWith("lit") == true && fromTarget?.label?.startsWith("lit") == true) {
            if (fromPattern.originalLabel?.toDoubleOrNull() == fromTarget.originalLabel?.toDoubleOrNull()) {
                return 0
            }
        }

        // Otherwise check only labels and original labels
        if (fromTarget?.originalLabel.equals(fromPattern?.originalLabel, ignoreCase = true)
            && fromTarget?.label.equals(fromPattern?.label, ignoreCase = true)
        ) {
            return 0
        }
        return 1
    }

}