package org.jetbrains.research.plugin.gumtree.wrappers

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex

@Serializable
class PyPsiGumTreeWrapper {

    @Transient
    var rootVertex: PatternSpecificVertex? = null
    val type: Int
    val label: String

    constructor(initialTree: PyPsiGumTree) {
        this.rootVertex = initialTree.rootVertex
        this.type = initialTree.type
        this.label = initialTree.label
    }

    fun getNode(): PyPsiGumTree {
        val node = PyPsiGumTree(type, label)
        node.rootVertex = rootVertex!!
        return node
    }
}