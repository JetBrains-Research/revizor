package org.jetbrains.research.jgrapht

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

