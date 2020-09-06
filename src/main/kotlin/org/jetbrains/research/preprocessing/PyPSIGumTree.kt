package org.jetbrains.research.preprocessing

import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.pyflowgraph.getFullName
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPSIGumTree(private val rootElement: PyElement) : Tree(rootElement.getType(), rootElement.getFullName()) {

    private val fieldNames: MutableList<String> = arrayListOf()

    fun addChildWithField(t: PyPSIGumTree, fieldName: String) {
        children.add(t)
        t.parent = this
        fieldNames.add(fieldName)
    }
}