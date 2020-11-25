package org.jetbrains.research.common.gumtree

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.common.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.common.pyflowgraph.getType


class PyPsiGumTree : Tree {
    var rootElement: PyElement? = null
    var rootVertex: PatternSpecificVertex? = null

    constructor(rootElement: PyElement?) : super(rootElement?.getType() ?: -1, rootElement?.toString()) {
        this.rootElement = rootElement
    }

    constructor(type: Int, label: String) : super(type, label)

    fun addPsiChild(child: ITree) {
        child.parent = this
        children.add(child)
    }
}