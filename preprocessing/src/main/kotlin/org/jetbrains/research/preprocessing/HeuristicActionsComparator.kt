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
        // The following code is needed to correctly match parts of the fully qualified names,
        // e. g. `collections` and `types` in case of `isinstance -> callable` pattern,
        // because we only have `collections.Callable` and `types.FunctionType` among the labels.

        labelsGroups = arrayListOf()
        for (group in originalLabelsGroups) {
            val extendedGroup = hashSetOf("") // to match elements without labels, e. g. `PyTupleExpression`
            for (label in group) {
                extendedGroup.addAll(label.split("."))
            }
            labelsGroups.add(extendedGroup)
        }
    }


    fun actionsHeuristicallyEquals(first: Action, second: Action): Boolean {
        if (first === second) return true
        if (first.toString() == second.toString()) return true
        if (first.name != second.name) return false

        val (firstPsiTypeBefore, firstLabelBefore) =
            first.node.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
        val (secondPsiTypeBefore, secondLabelBefore) =
            second.node.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)

        when {
            first is Update && second is Update -> {
                val (firstPsiTypeAfter, firstLabelAfter) = first.value.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                val (secondPsiTypeAfter, secondLabelAfter) = second.value.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                if (firstPsiTypeBefore != secondPsiTypeBefore || firstPsiTypeAfter != secondPsiTypeAfter) {
                    return false
                }
                return compareByLabels(
                    Pair(firstLabelBefore, firstLabelAfter),
                    Pair(secondLabelBefore, secondLabelAfter)
                )
            }
            first is Delete && second is Delete -> {
                if (firstPsiTypeBefore != secondPsiTypeBefore) {
                    return false
                }
                var labelsBeforeMatched = false
                for (labels in labelsGroups) {
                    if (labels.contains(firstLabelBefore) && labels.contains(secondLabelBefore)) {
                        labelsBeforeMatched = true
                    }
                }
                return labelsBeforeMatched
            }
            first is Addition && second is Addition -> {
                if (first.position != second.position) return false
                val (firstParentPsiType, firstParentLabel) =
                    first.parent.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                val (secondParentPsiType, secondParentLabel) =
                    second.parent.label.splitWithDefault(PSI_ELEMENT_DESC_DELIMITER)
                if (firstPsiTypeBefore != secondPsiTypeBefore || firstParentPsiType != secondParentPsiType) {
                    return false
                }
                return compareByLabels(
                    Pair(firstLabelBefore, firstParentLabel),
                    Pair(secondLabelBefore, secondParentLabel)
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