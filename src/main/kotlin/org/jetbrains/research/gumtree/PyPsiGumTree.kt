package org.jetbrains.research.gumtree

import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPsiGumTree(var rootElement: PyElement?) : Tree(
    rootElement?.getType() ?: -1,
    rootElement?.toString()
) {

    private val typeOfPrevSibling: MutableList<Class<PsiElement>?> = arrayListOf()

    fun addPsiChild(child: ITree) {
        child.parent = this
        typeOfPrevSibling.add((children.lastOrNull() as PyPsiGumTree?)?.rootElement?.javaClass)
        children.add(child)
    }

    fun getTypeOfPrevSibling(pos: Int) = typeOfPrevSibling[pos]
}