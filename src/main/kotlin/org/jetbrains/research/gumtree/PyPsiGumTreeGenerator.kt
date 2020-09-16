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
        for ((name, childContainer) in childContainerByName) {
            when (childContainer) {
                is EmptyChildContainer -> {
                    gumtree.addChildWithName(child = Tree(-1, "EmptyStub"), fieldName = name)
                }
                is OneChildContainer -> {
                    val childGumtree = visit(element = childContainer.element, parent = gumtree)
                    gumtree.addChildWithName(child = childGumtree, fieldName = name)
                }
                is ManyChildrenContainer -> {
                    val arrayGumTreeStub = Tree(-2, "$element->$name")
                    gumtree.addChildWithName(child = arrayGumTreeStub, fieldName = name)
                    for (subChild: PyElement in childContainer.elements) {
                        val subChildGumtree = visit(element = subChild, parent = gumtree)
                        arrayGumTreeStub.addChild(subChildGumtree)
                    }
                }
            }
        }
        return gumtree
    }
}