package org.jetbrains.research.gumtree

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

    fun applyUpdate(element: PyElement, action: Update) {
        val newClassname: String = action.value.substringBefore(":").trim()
        val newValue: String = action.value.substringAfter(":").trim()
        val patternElement: PyElement? = (action.node as PyPsiGumTree).rootElement
        if (element.toString() == patternElement.toString()) {
            when (element) {
                is PyCallExpression -> {
                    val newElement: PyCallExpression =
                        generator.createCallExpression(Config.LANGUAGE_LEVEL, newValue)
                    for (argument in element.arguments) {
                        newElement.argumentList?.addArgument(argument)
                    }
                    execute { element.replace(newElement) }
                }
                is PyReferenceExpression -> {
                    val newElement: PyReferenceExpression =
                        generator.createExpressionFromText(Config.LANGUAGE_LEVEL, newValue) as PyReferenceExpression
                    execute { element.replace(newElement) }
                }
                else -> TODO("Not yet implemented")
            }
        } else {
            throw IllegalStateException("The current node does not match the node from the UPDATE action")
        }
    }

    fun applyMove(element: PyElement, parentElement: PyElement, action: Move): PyElement {
        val movedPatternElement: PyElement? = (action.node as PyPsiGumTree).rootElement
        val parentPatternElement: PyElement? = (action.parent as PyPsiGumTree).rootElement
        if (movedPatternElement.toString() == element.toString()
            && parentPatternElement.toString() == parentElement.toString()
        ) {
            val insertedElement = applyInsert(parentElement, Insert(action.node, action.parent, action.position))
            applyUpdate(insertedElement, Update(PyPsiGumTree(insertedElement), element.toString()))
            applyDelete(element, Delete(action.node))
            return insertedElement
        } else {
            throw IllegalStateException("The current node does not match the node from the MOVE action")
        }
    }

    fun applyInsert(parentElement: PyElement, action: Insert): PyElement {
        val parentPatternElement: PyElement? = (action.parent as PyPsiGumTree).rootElement
        if (parentElement.toString() == parentPatternElement.toString()) {
            when ((action.node as PyPsiGumTree).rootElement) {
                is PyTupleExpression -> {
                    val newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, "(None, None)")
                    execute {
                        if (action.position < parentElement.children.size) {
                            parentElement.children[action.position].replace(newElement)
                        } else {
                            parentElement.addAfter(newElement, parentElement.lastChild)
                        }
                    }
                    return newElement
                }
                else -> TODO("Not yet implemented")
            }
        } else {
            throw IllegalStateException("The current node does not match the node from the INSERT action")
        }
    }

    fun applyDelete(element: PyElement, action: Delete) {
        val patternElement: PyElement? = (action.node as PyPsiGumTree).rootElement
        if (element.toString() == patternElement.toString()) {
            execute { element.delete() }
        } else {
            throw IllegalStateException("The current node does not match the node from the DELETE action")
        }
    }

    private fun execute(command: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project) {
            command()
        }
    }
}