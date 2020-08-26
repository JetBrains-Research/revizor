package org.jetbrains.research.plugin.jgrapht.edges

/**
 * An edges needed for the JGraphtT interlayer.
 */

data class PatternSpecificMultipleEdge(
    private val id: Int,
    val embeddedEdgeByXlabel: MutableMap<String?, PatternSpecificEdge>
)

data class PatternSpecificEdge(
    private val id: Int,
    val xlabel: String? = null,
    val fromClosure: Boolean? = null,
    val style: String? = null
)

