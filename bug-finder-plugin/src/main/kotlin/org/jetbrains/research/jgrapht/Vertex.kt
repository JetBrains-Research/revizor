package org.jetbrains.research.jgrapht

data class Vertex(
    val id: String,
    val label: String?,
    val originalLabel: String?,
    val color: String?,
    val shape: String?,
    var longestCommonSuffix: String? = null
)

class PatternVertexComparator : Comparator<Vertex> {
    override fun compare(fromTarget: Vertex?, fromPattern: Vertex?): Int {
        if (fromTarget?.label?.startsWith("var") == true
            && fromPattern?.label?.startsWith("var") == true
        ) {
            val lcs = fromPattern.longestCommonSuffix ?: ""
            return if (fromTarget.originalLabel?.endsWith(lcs) == true) 0 else 1
        }
        if (fromTarget?.originalLabel == fromPattern?.originalLabel && fromTarget?.label == fromPattern?.label)
            return 0
        return 1
    }
}