package org.jetbrains.research.common

import org.jgrapht.graph.DefaultEdge


data class Vertex(val id: String, val label: String?, val color: String?, val shape: String?)
data class Edge(val id: DefaultEdge, val xlabel: String?, val from_closure: Boolean?, val style: String?)
