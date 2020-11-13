package org.jetbrains.research.plugin.gumtree

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPsiGumTree(var rootElement: PyElement?) : Tree(
    rootElement?.getType() ?: -1,
    rootElement?.toString()
) {
    var rootVertex: PatternSpecificVertex? = null

    fun addPsiChild(child: ITree) {
        child.parent = this
        children.add(child)
    }
}