package org.jetbrains.research.gumtree

import com.github.gumtreediff.tree.TreeContext
import com.jetbrains.python.psi.PyElement


class PyPsiGumTreeGenerator {

    private val context = TreeContext()
    private var nodeId: Int = 0

    fun generate(rootPsiElement: PyElement): TreeContext {
        context.root = visit(rootPsiElement)
        context.root.refresh()
        return context
    }

    private fun visit(element: PyElement, parent: PyPsiGumTree? = null): PyPsiGumTree {
        val gumtree = PyPsiGumTree(element)
        gumtree.parent = parent
        gumtree.id = nodeId++
        for (child in element.children.filterIsInstance<PyElement>()) {
            val childGumtree = visit(element = child, parent = gumtree)
            gumtree.addPsiChild(childGumtree)
        }
        return gumtree
    }
}