package org.jetbrains.research.preprocessing

import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import com.intellij.util.IntPair
import java.util.*

fun getLongestCommonSuffix(strings: Collection<String?>?): String {
    if (strings == null || strings.isEmpty())
        return ""
    var lcs = strings.first()
    for (string in strings) {
        lcs = lcs?.commonSuffixWith(string ?: "")
    }
    return lcs ?: ""
}

fun getLongestCommonEditActionsSubsequence(first: List<Action>, second: List<Action>): List<Action> {
    return getLongestCommonSubsequence(first, second, elementsEquals = ::actionsHeuristicallyEquals)
}

fun <T> getLongestCommonSubsequence(first: List<T>, second: List<T>, elementsEquals: (T, T) -> Boolean): List<T> {
    if (first.isEmpty() || second.isEmpty()) return arrayListOf()

    val lcs = Array(first.size + 1) { IntArray(second.size + 1) }
    val prev = Array(first.size + 1) { Array(second.size + 1) { IntPair(0, 0) } }
    val result = arrayListOf<T>()

    lcs[0][0] = 0
    for (i in 1..first.size)
        lcs[i][0] = 0
    for (j in 1..second.size)
        lcs[0][j] = 0

    for (i in 1..first.size) {
        for (j in 1..second.size) {
            if (elementsEquals(first[i - 1], second[j - 1])) {
                lcs[i][j] = lcs[i - 1][j - 1] + 1
                prev[i][j] = IntPair(i - 1, j - 1)
            } else {
                if (lcs[i - 1][j] >= lcs[i][j - 1]) {
                    lcs[i][j] = lcs[i - 1][j]
                    prev[i][j] = IntPair(i - 1, j)
                } else {
                    lcs[i][j] = lcs[i][j - 1]
                    prev[i][j] = IntPair(i, j - 1)
                }
            }
        }
    }

    fun collectResultingSubsequence(i: Int, j: Int) {
        if (i == 0 || j == 0) return
        if (prev[i][j] == IntPair(i - 1, j - 1)) {
            collectResultingSubsequence(i - 1, j - 1)
            result.add(first[i - 1])
        } else {
            if (prev[i][j] == IntPair(i - 1, j))
                collectResultingSubsequence(i - 1, j)
            else
                collectResultingSubsequence(i, j - 1)
        }
    }

    collectResultingSubsequence(first.size, second.size)
    return result
}

fun actionsHeuristicallyEquals(first: Action, second: Action): Boolean {
    if (first === second) return true
    return first.toString() == second.toString() // FIXME: var names within the actions
}

fun sortEditActions(actions: List<Action>) {
    val updates = arrayListOf<Pair<Int, Update>>()
    for ((i, action) in actions.withIndex()) {
        if (action is Update) {
            updates.add(Pair(i, action))
            continue
        }
        if (action is Move) {
            val item = updates.find { it.second.node.hasSameTypeAndLabel(action.node) } ?: continue
            Collections.swap(actions, i, item.first)
        }
    }
}