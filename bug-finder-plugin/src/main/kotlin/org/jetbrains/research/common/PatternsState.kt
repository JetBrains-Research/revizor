package org.jetbrains.research.common

import org.jgrapht.Graph
import java.nio.file.Path

object PatternsState {
    val patternsGraphs = HashMap<Path, Graph<Vertex, MultipleEdge>>()
}