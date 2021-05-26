package org.jetbrains.research.preprocessing

import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.actions.model.Addition
import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Update

class HeuristicActionsComparator(originalLabelsGroups: List<Set<String>>) {
    companion object {
        const val PSI_ELEMENT_DESC_DELIMITER = ": "
    }

    private val labelsGroups: MutableList<MutableSet<String>>

    init {
        // The following code is needed to correctly match the parts of the fully qualified names,
        // e. g. `collections` and `types` in case of `isinstance -> callable` pattern,
        // because we only have `collections.Callable` and `types.FunctionType` among the labels.

        labelsGroups = arrayListOf()
        for (group in originalLabelsGroups) {
            val extendedGroup = hashSetOf("") // to match elements without labels, e. g. `PyTupleExpression`
            for (label in group) {
                extendedGroup.addAll(label.split("."))
                extendedGroup.add(label)
            }
            labelsGroups.add(extendedGroup)
        }
    }


    fun actionsHeuristicallyEquals(first: Action, second: Action): Boolean {
        if (first === second) return true
        if (first.toString() == second.toString()) return true
        if (first.name != second.name) return false

        val (firstSourcePsiType, firstSourceElementLabel) =
            first.node.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
        val (secondSourcePsiType, secondSourceElementLabel) =
            second.node.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)

        when {
            first is Update && second is Update -> {
                val (firstTargetPsiType, firstTargetElementLabel) = first.value.splitWithDefault(
                    PSI_ELEMENT_DESC_DELIMITER
                )
                val (secondTargetPsiType, secondTargetElementLabel) = second.value.splitWithDefault(
                    PSI_ELEMENT_DESC_DELIMITER
                )
                if (firstSourcePsiType != secondSourcePsiType || firstTargetPsiType != secondTargetPsiType) {
                    return false
                }
                return compareByLabels(
                    Pair(firstSourceElementLabel, firstTargetElementLabel),
                    Pair(secondSourceElementLabel, secondTargetElementLabel)
                )
            }
            first is Delete && second is Delete -> {
                if (firstSourcePsiType != secondSourcePsiType) {
                    return false
                }
                var labelsBeforeMatched = false
                for (labels in labelsGroups) {
                    if (labels.contains(firstSourceElementLabel) && labels.contains(secondSourceElementLabel)) {
                        labelsBeforeMatched = true
                    }
                }
                return labelsBeforeMatched
            }
            first is Addition && second is Addition -> {
                if (first.position != second.position) return false
                val (firstParentPsiType, firstParentElementLabel) =
                    first.parent.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                val (secondParentPsiType, secondParentElementLabel) =
                    second.parent.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                if (firstSourcePsiType != secondSourcePsiType || firstParentPsiType != secondParentPsiType) {
                    return false
                }
                return compareByLabels(
                    Pair(firstSourceElementLabel, firstParentElementLabel),
                    Pair(secondSourceElementLabel, secondParentElementLabel)
                )
            }
            else -> return false
        }
    }

    private fun compareByLabels(
        firstActionLabels: Pair<String, String>,
        secondActionLabels: Pair<String, String>
    ): Boolean {
        var labelsBeforeMatched = false
        var labelsAfterMatched = false
        for (labels in labelsGroups) {
            if (labels.contains(firstActionLabels.first) && labels.contains(secondActionLabels.first)) {
                labelsBeforeMatched = true
            }
            if (labels.contains(firstActionLabels.second) && labels.contains(secondActionLabels.second)) {
                labelsAfterMatched = true
            }
        }
        return labelsBeforeMatched && labelsAfterMatched
    }

    private fun CharSequence.splitWithDefault(delimiter: String, default: String = ""): List<String> {
        return if (this.contains(delimiter)) {
            this.split(delimiter)
        } else {
            listOf(this.toString(), default)
        }
    }
}