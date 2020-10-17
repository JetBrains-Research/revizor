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
        val newElement: PyElement
        when (element) {
            is PyCallExpression -> {
                newElement = generator.createCallExpression(Config.LANGUAGE_LEVEL, newValue)
                for (argument in element.arguments) {
                    newElement.argumentList?.addArgument(argument)
                }
            }
            is PyReferenceExpression -> {
                newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, newValue) as PyReferenceExpression
            }
            else -> TODO("Not yet implemented")
        }
        execute { element.replace(newElement) }
        return newElement
    }

    fun applyMove(element: PyElement, parentElement: PyElement, action: Move): PyElement {
        val elementCopy = element.copy() as PyElement
        execute { element.delete() }
        executeInsert(elementCopy, parentElement, action.position)
        return parentElement.children[action.position] as PyElement
    }

    fun applyInsert(parentElement: PyElement, action: Insert): PyElement {
        val newNodeClassName = action.node.label.substringBefore(":").trim()
        val newNodeValue = action.node.label.substringAfter(":").trim()
        val newElement: PyElement
        when (newNodeClassName) {
            "PyTupleExpression" -> {
                newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, "(_,)")
                        .let { (it as PyParenthesizedExpression).containedExpression!! }
            }
            "PyCallExpression" -> {
                newElement = generator.createCallExpression(Config.LANGUAGE_LEVEL, newNodeValue)
            }
            "PyStatementList" -> {
                newElement = generator
                        .createFromText(Config.LANGUAGE_LEVEL, PyFunction::class.java, "def foo():\n\tpass\n")
                        .statementList
                newElement.children[0].delete()
            }
            "PyTargetExpression" -> {
                newElement = generator
                        .createFromText(Config.LANGUAGE_LEVEL, PyAssignmentStatement::class.java, "$newNodeValue=None")
                        .targets[0] as PyTargetExpression
            }
            "PyPassStatement" -> {
                newElement = generator.createPassStatement()
            }
            "PyReferenceExpression" -> {
                newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, newNodeValue)
                        as PyReferenceExpression
            }
            else -> TODO("Not yet implemented")
        }
        executeInsert(newElement, parentElement, action.position)
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