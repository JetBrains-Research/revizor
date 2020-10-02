package org.jetbrains.research.gumtree

import com.github.gumtreediff.actions.model.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*
import org.jetbrains.research.plugin.Config

class PyElementTransformer(var project: Project) {

    private val generator = PyElementGenerator.getInstance(project)

    fun applyAction(element: PyElement, action: Action) {
        when (action) {
            is Update -> applyUpdate(element, action)
            is Delete -> applyDelete(element, action)
            is Insert -> applyInsert(element, action)
            is Move -> applyMove(element, action)
            else -> throw IllegalStateException()
        }
    }

    private fun applyUpdate(element: PyElement, action: Update) {
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
            throw IllegalStateException("Current node doesn't match to a node from an action")
        }
    }

    private fun applyMove(element: PyElement, action: Move) {
        TODO("Not yet implemented")
    }

    private fun applyInsert(element: PyElement, action: Insert) {
        val patternElement: PyElement? = (action.node as PyPsiGumTree).rootElement
        val parent: PyPsiGumTree = action.parent as PyPsiGumTree                                    // FIXME
        val typeOfPrevSibling = parent.getTypeOfPrevSibling(action.position)
        val prevSibling = typeOfPrevSibling?.let { PsiTreeUtil.getChildOfType(parent.rootElement, it) }
        if (element.toString() == patternElement.toString()) {
            when (element) {
                is PyTupleExpression -> {
                    val newElement = generator.createExpressionFromText(Config.LANGUAGE_LEVEL, "(None, None)")
                    execute {
                        if (prevSibling == null) {
                            parent.rootElement?.addBefore(newElement, parent.rootElement?.firstChild)
                        } else {
                            parent.rootElement?.addAfter(newElement, prevSibling)
                        }
                    }
                }
            }
        }
    }

    private fun applyDelete(element: PyElement, action: Delete) {
        val patternElement: PyElement? = (action.node as PyPsiGumTree).rootElement
        if (element.toString() == patternElement.toString()) {
            execute { element.delete() }
        } else {
            throw IllegalStateException("Current node doesn't match to a node from an action")
        }
    }

    private fun execute(command: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project) {
            command()
        }
    }
}