package org.jetbrains.research.gumtree

import com.github.gumtreediff.tree.TreeContext
import com.jetbrains.python.psi.PyElement
import java.lang.reflect.Modifier


class PyPSIGumTreeGenerator {

    private val context = TreeContext()
    private var nodeId: Int = 0

    fun generate(rootPsiElement: PyElement): TreeContext {
        context.root = visit(rootPsiElement)
        context.root.refresh()
        return context
    }

    private fun visit(psiNode: PyElement, parent: PyPSIGumTree? = null): PyPSIGumTree {
        val gumtree = PyPSIGumTree(psiNode)
        gumtree.parent = parent
        gumtree.id = nodeId++
        val children = psiNode.children.toSet()
        val methods = psiNode.javaClass.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .filter {
                PyElement::class.java.isAssignableFrom(it.returnType)
                        || Array<PyElement>::class.java.isAssignableFrom(it.returnType)
            }
        for (method in methods) {
            val potentialChildren = try {
                method.invoke(psiNode)?.let { if (it is PyElement) arrayOf(it) else it }
            } catch (ex: Exception) {
                continue
            } ?: continue
            if (potentialChildren is Array<*>) {
                for (potentialChild in potentialChildren) {
                    if (children.contains(potentialChild)) {
                        val childGumtree = visit(psiNode = potentialChild as PyElement, parent = gumtree)
                        gumtree.addChildWithField(childGumtree, method.name)
                    }
                }
            }
        }
        return gumtree
    }
}