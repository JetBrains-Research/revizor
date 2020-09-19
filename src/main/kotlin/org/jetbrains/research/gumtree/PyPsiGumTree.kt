package org.jetbrains.research.gumtree

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPsiGumTree(var rootElement: PyElement?) : Tree(
    rootElement?.getType() ?: -1,
    rootElement?.toString()
) {

    private val fieldNames: MutableList<String> = arrayListOf()

    fun addChildWithName(child: ITree, fieldName: String) {
        children.add(child)
        child.parent = this
        fieldNames.add(fieldName)
    }

    fun getFieldNameAtPosition(pos: Int): String = fieldNames[pos]
}