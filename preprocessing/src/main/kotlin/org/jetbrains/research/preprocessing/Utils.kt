package org.jetbrains.research.preprocessing

import com.github.gumtreediff.actions.model.Action
import com.intellij.util.IntPair

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
    if (first.isEmpty() || second.isEmpty()) return arrayListOf()

    val lceas = Array(first.size) { IntArray(second.size) }
    val prev = Array(first.size) { Array(second.size) { IntPair(0, 0) } }

    lceas[0][0] = if (first[0].heuristicallyEquals(second[0])) 1 else 0

    for (i in 1 until first.size)
        lceas[i][0] = if (first[i].heuristicallyEquals(second[0])) 1 else maxOf(lceas[i - 1][0], 0)
    for (j in 1 until second.size)
        lceas[0][j] = if (first[0].heuristicallyEquals(second[j])) 1 else maxOf(lceas[0][j - 1], 0)

    for (i in 1 until first.size) {
        for (j in 1 until second.size) {
            if (first[i].heuristicallyEquals(second[i])) {
                lceas[i][j] = lceas[i - 1][j - 1] + 1
                prev[i][j] = IntPair(i - 1, j - 1)
            } else {
                if (lceas[i - 1][j] >= lceas[i][j - 1]) {
                    lceas[i][j] = lceas[i - 1][j]
                    prev[i][j] = IntPair(i - 1, j)
                } else {
                    lceas[i][j] = lceas[i][j - 1]
                    prev[i][j] = IntPair(i, j - 1)
                }
            }
        }
    }

    val result = arrayListOf<Action>()

    fun collectResultingSubsequence(i: Int, j: Int) {
        if (i == 0 || j == 0) {
            if (first[i].heuristicallyEquals(second[j]))
                result.add(first[i])
            return
        }
        if (prev[i][j] == IntPair(i - 1, j - 1)) {
            collectResultingSubsequence(i - 1, j - 1)
            result.add(first[i])
        } else {
            if (prev[i][j] == IntPair(i - 1, j))
                collectResultingSubsequence(i - 1, j)
            else
                collectResultingSubsequence(i, j - 1)
        }
    }

    collectResultingSubsequence(first.lastIndex, second.lastIndex)
    return result
}

fun Action.heuristicallyEquals(other: Any): Boolean {
    if (other !is Action) return false
    if (other === this) return true
    return this.toString() == other.toString()
}