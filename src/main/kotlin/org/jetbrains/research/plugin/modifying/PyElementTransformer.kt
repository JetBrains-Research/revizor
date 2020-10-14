package org.jetbrains.research.plugin.modifying

import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Insert
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.jetbrains.python.psi.*
import org.jetbrains.research.plugin.Config

class PyElementTransformer(var project: Project) {

    private val generator = PyElementGenerator.getInstance(project)

    fun applyUpdate(element: PyElement, action: Update): PyElement {
        val newValue: String = action.value.substringAfter(":").trim()
        when (element) {
            is PyCallExpression -> {
                val newElement: PyCallExpression =
                        generator.createCallExpression(Config.LANGUAGE_LEVEL, newValue)
                for (argument in element.arguments) {
                    newElement.argumentList?.addArgument(argument)
                }
                execute { element.replace(newElement) }
                return newElement
            }
            is PyReferenceExpression -> {
                val newElement: PyReferenceExpression =
                        generator.createExpressionFromText(Config.LANGUAGE_LEVEL, newValue) as PyReferenceExpression
                execute { element.replace(newElement) }
                return newElement
            }
            else -> TODO("Not yet implemented")
        }
    }

    fun applyMove(element: PyElement, parentElement: PyElement, action: Move): PyElement {
        val elementCopy = element.copy() as PyElement
        execute { element.delete() }
        executeInsert(elementCopy, parentElement, action.position)
        return parentElement.children[action.position] as PyElement
    }

    fun applyInsert(parentElement: PyElement, action: Insert): PyElement {
        val newNodeClassName = action.node.label.substringBefore(":", "").trim()
        val newNodeValue = action.node.label.substringAfter(":", "").trim()
        when (newNodeClassName) {
            "PyTupleExpression" -> {
                val newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, "(None, None)")
                        .let { (it as PyParenthesizedExpression).containedExpression!! }
                executeInsert(newElement, parentElement, action.position)
            }
            "PyCallExpression" -> {
                val newElement = generator.createCallExpression(Config.LANGUAGE_LEVEL, newNodeValue)
                executeInsert(newElement, parentElement, action.position)
            }
            "PyStatementList" -> {
                val newElement = (action.node as PyPsiGumTree).rootElement!!.copy() as PyElement // FIXME: rootElement call
                executeInsert(newElement, parentElement, action.position)
            }
            else -> TODO("Not yet implemented")
        }
        return parentElement.children[action.position] as PyElement
    }

    fun applyDelete(element: PyElement, action: Delete) {
        execute { element.delete() }
    }

    private fun execute(command: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project) {
            command()
        }
    }

    private fun executeInsert(element: PyElement, parent: PyElement, position: Int) {
        execute {
            if (position < parent.children.size) {
                parent.children[position].replace(element)
            } else {
                parent.addAfter(element, parent.lastChild)
            }
        }
    }
}