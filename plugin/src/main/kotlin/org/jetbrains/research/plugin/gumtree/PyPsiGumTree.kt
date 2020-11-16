package org.jetbrains.research.plugin.gumtree

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPsiGumTree : Tree {
    var rootElement: PyElement? = null
    var rootVertex: PatternSpecificVertex? = null

    constructor(rootElement: PyElement?) {
        this.rootElement = rootElement
        this.type = rootElement?.getType() ?: -1
        this.label = rootElement?.toString()
    }

    constructor(type: Int, label: String) {
        this.type = type
        this.label = label
    }

    fun addPsiChild(child: ITree) {
        child.parent = this
        children.add(child)
    }
}