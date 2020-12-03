package org.jetbrains.research.common.gumtree.wrappers

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.research.common.gumtree.PyPsiGumTree
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex

@Serializable
class PyPsiGumTreeWrapper {

    @Transient
    var rootVertex: PatternSpecificVertex? = null
    val rootVertexId: Int?
    val type: Int
    val label: String

    constructor(initialTree: PyPsiGumTree) {
        this.rootVertex = initialTree.rootVertex
        this.rootVertexId = this.rootVertex?.id
        this.type = initialTree.type
        this.label = initialTree.label
    }

    fun getNode(): PyPsiGumTree {
        val node = PyPsiGumTree(type, label)
        node.rootVertex = rootVertex
        return node
    }
}