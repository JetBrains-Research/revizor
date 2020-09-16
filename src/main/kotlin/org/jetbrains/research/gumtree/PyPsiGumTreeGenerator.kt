package org.jetbrains.research.gumtree

import com.github.gumtreediff.tree.Tree
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
        val childContainerByName: Map<String, PyElementChild> = extractChildrenByFieldName(element)
        for ((fieldName, childContainer) in childContainerByName) {
            when (childContainer) {
                is EmptyChildContainer -> {
                    val emptyChildStub = Tree(-1, "EmptyStub")
                    emptyChildStub.id = nodeId++
                    gumtree.addChildWithName(child = emptyChildStub, fieldName = fieldName)
                }
                is OneChildContainer -> {
                    val childGumtree = visit(element = childContainer.element, parent = gumtree)
                    gumtree.addChildWithName(child = childGumtree, fieldName = fieldName)
                }
                is ManyChildrenContainer -> {
                    val arrayOfChildrenStub = Tree(-2, "$element->$fieldName")
                    arrayOfChildrenStub.id = nodeId++
                    gumtree.addChildWithName(child = arrayOfChildrenStub, fieldName = fieldName)
                    for (subChild: PyElement in childContainer.elements) {
                        val subChildGumtree = visit(element = subChild, parent = gumtree)
                        arrayOfChildrenStub.addChild(subChildGumtree)
                    }
                }
            }
        }
        return gumtree
    }
}