package org.jetbrains.research.gumtree

import com.github.gumtreediff.actions.model.*
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyElementGenerator
import org.jetbrains.research.plugin.Config
import org.jetbrains.research.plugin.PatternsStorage

class PyPsiGumTreeTransformer {

    private val generator = PyElementGenerator.getInstance(PatternsStorage.project)

    fun applyAction(tree: PyPsiGumTree, action: Action) {
        when (action) {
            is Update -> applyUpdate(tree, action)
            is Delete -> applyDelete(tree, action)
            is Insert -> applyInsert(tree, action)
            is Move -> applyMove(tree, action)
        }
    }

    private fun applyMove(tree: PyPsiGumTree, action: Move) {
        TODO("Not yet implemented")
    }

    private fun applyInsert(tree: PyPsiGumTree, action: Insert) {
        TODO("Not yet implemented")
    }

    private fun applyDelete(tree: PyPsiGumTree, action: Delete) {
        TODO("Not yet implemented")
    }

    private fun applyUpdate(tree: PyPsiGumTree, action: Update) {
        val newPyElementClassName = action.value.substringBefore(":").trim()
        val newValue = action.value.substringAfter(":").trim()
        val oldNodeFromAction = (action.node as PyPsiGumTree).rootElement
        val currentOldNode = tree.rootElement
        if (currentOldNode.toString() == oldNodeFromAction.toString() &&
            newPyElementClassName == oldNodeFromAction!!::class.simpleName
        ) {
            when (currentOldNode) {
                is PyCallExpression -> {
                    val newNode: PyCallExpression = generator.createCallExpression(Config.LANGUAGE_LEVEL, newValue)
                    for (argument in currentOldNode.arguments) {
                        newNode.argumentList?.addArgument(argument)
                    }
                    tree.rootElement = newNode
                    tree.label = newNode.toString()
                }
            }
        } else {
            throw IllegalStateException("Current node doesn't match to a node from an action")
        }
    }
}