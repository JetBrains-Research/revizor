package org.jetbrains.research.gumtree

import com.github.gumtreediff.actions.model.Update
import com.github.gumtreediff.tree.ITree
import com.github.gumtreediff.tree.Tree
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementGenerator
import org.jetbrains.research.plugin.Config
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.pyflowgraph.getType


class PyPsiGumTree(val rootElement: PyElement?) : Tree(
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

    fun applyUpdateAction(action: Update) {
        val oldNode = (action.node as PyPsiGumTree).rootElement
        val newValue = action.value.substringAfter(":").trim()
        val generator = PyElementGenerator.getInstance(PatternsStorage.project)
        when (oldNode) {
            is PyCallExpression -> {
                val newNode = generator.createCallExpression(Config.LANGUAGE_LEVEL, newValue)
                oldNode.replace(newNode)
            }
        }
    }
}