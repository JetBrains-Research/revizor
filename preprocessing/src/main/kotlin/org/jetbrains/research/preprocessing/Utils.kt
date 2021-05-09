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
    return getLongestCommonSubsequence(first, second, elementsEquals = ::actionsHeuristicallyEquals)
}

fun <T> getLongestCommonSubsequence(first: List<T>, second: List<T>, elementsEquals: (T, T) -> Boolean): List<T> {
    if (first.isEmpty() || second.isEmpty()) return arrayListOf()

    val lcs = Array(first.size) { IntArray(second.size) }
    val prev = Array(first.size) { Array(second.size) { IntPair(0, 0) } }
    val result = arrayListOf<T>()

    lcs[0][0] = if (elementsEquals(first[0], second[0])) 1 else 0
    for (i in 1 until first.size)
        lcs[i][0] = if (elementsEquals(first[i], second[0])) 1 else maxOf(lcs[i - 1][0], 0)
    for (j in 1 until second.size)
        lcs[0][j] = if (elementsEquals(first[0], second[j])) 1 else maxOf(lcs[0][j - 1], 0)

    for (i in 1 until first.size) {
        for (j in 1 until second.size) {
            if (elementsEquals(first[i], second[i])) {
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
        if (i == 0 || j == 0) {
            if (elementsEquals(first[i], second[j]))
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

fun actionsHeuristicallyEquals(first: Action, second: Action): Boolean {
    if (first === second) return true
    return first.toString() == second.toString()
}