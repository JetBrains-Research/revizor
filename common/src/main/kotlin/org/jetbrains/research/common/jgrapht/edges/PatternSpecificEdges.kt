package org.jetbrains.research.common.jgrapht.edges

/**
 * An edges needed for the JGraphtT interlayer.
 */

data class PatternSpecificMultipleEdge(
    val id: Int,
    val embeddedEdgeByXlabel: MutableMap<String?, PatternSpecificEdge>,
    var metadata: String? = null
)

data class PatternSpecificEdge(
    val id: Int,
    val xlabel: String? = null,
    val fromClosure: Boolean? = null,
    val style: String? = null
)

