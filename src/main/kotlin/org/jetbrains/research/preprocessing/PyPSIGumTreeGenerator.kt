package org.jetbrains.research.preprocessing

import com.github.gumtreediff.tree.TreeContext
import com.jetbrains.python.psi.PyElement


class PyPSIGumTreeGenerator {

    private val context = TreeContext()

    fun generate(rootPsiElement: PyElement): TreeContext {
        context.root = visit(rootPsiElement)
        context.root.refresh()
        return context
    }

    private fun visit(psiNode: PyElement, parent: PyPSIGumTree? = null): PyPSIGumTree {
        val gumtree = PyPSIGumTree(psiNode)
        gumtree.parent = parent
        for (childPsiNode in psiNode.children.filterIsInstance<PyElement>()) {
            gumtree.addChild(visit(psiNode = childPsiNode, parent = gumtree))
        }
        return gumtree
    }
}