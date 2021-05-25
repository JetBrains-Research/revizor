package org.jetbrains.research.common.pyflowgraph

import com.intellij.psi.util.elementType
import com.jetbrains.python.psi.*


fun PyElement.getFullName(): String =
    when (this) {
        is PyFunction, is PyNamedParameter -> this.name ?: ""
        is PyReferenceExpression, is PyTargetExpression -> {
            var fullName = (this as PyQualifiedExpression).referencedName
            var currentNode = this.qualifier
            while (currentNode is PyReferenceExpression
                || currentNode is PyCallExpression && currentNode.callee is PyReferenceExpression
            ) {
                if (currentNode is PyReferenceExpression) {
                    fullName = currentNode.referencedName + "." + fullName
                    currentNode = currentNode.qualifier
                } else {
                    // TODO: add argument reference name inside parenthesis?
                    fullName = (currentNode as PyCallExpression).callee?.name + "()." + fullName
                    currentNode = (currentNode.callee as PyReferenceExpression).qualifier
                }
            }
            fullName ?: ""
        }
        is PySubscriptionExpression -> {
            val operand = this.operand.getFullName()
            val index = this.indexExpression?.getFullName() ?: ""
            "$operand[$index]"
        }
        is PySliceExpression -> {
            val operand = this.operand.getFullName()
            val sliceItems = arrayListOf<String>()
            // FIXME: getNodeFullName for expressions
            this.sliceItem?.lowerBound?.let { sliceItems.add(it.getFullName()) }
            this.sliceItem?.stride?.let { sliceItems.add(it.getFullName()) }
            this.sliceItem?.upperBound?.let { sliceItems.add(it.getFullName()) }
            "$operand[${sliceItems.joinToString(":")}]"
        }
        is PyLiteralExpression -> "."
        is PyEmptyExpression -> ""
        else -> this.toString()
    }

fun PyElement.getType(): Int = this.elementType?.index?.toInt() ?: -1

fun PyElement.getKey(): String =
    when (this) {
        is PyFunction, is PyNamedParameter -> this.name ?: ""
        is PyReferenceExpression, is PyTargetExpression ->
            (this as PyQualifiedExpression).asQualifiedName()?.toString() ?: ""
        else -> throw IllegalArgumentException()
    }

fun PyElement.getShortName(): String =
    when (this) {
        is PyNamedParameter, is PyFunction -> this.name ?: ""
        is PyReferenceExpression, is PyTargetExpression -> (this as PyQualifiedExpression).referencedName ?: ""
        else -> throw IllegalArgumentException()
    }

fun PyElement.getOperationName(): String =
    when (this) {
        is PyAugAssignmentStatement -> {
            when (this.operation?.text) {
                "+=" -> "add"
                "-=" -> "sub"
                "*=" -> "mult"
                "/=" -> "div"
                "%=" -> "mod"
                "**=" -> "pow"
                ">>=" -> "rshift"
                "<<-" -> "lshift"
                "&=" -> "bitand"
                "|=" -> "bitor"
                "^=" -> "bitxor"
                else -> throw NotImplementedError("Unsupported operation type")
            }
        }
        is PyBinaryExpression -> {
            when (this.psiOperator?.text) {
                "+" -> "add"
                "-" -> "sub"
                "*" -> "mult"
                "**" -> "pow"
                "/" -> "div"
                "%" -> "mod"
                "and" -> "and"
                "or" -> "or"
                ">" -> "Gt"
                ">=" -> "GtE"
                "<" -> "Lt"
                "<=" -> "LtE"
                "==" -> "Eq"
                "!=" -> "NotEq"
                "in" -> "In"
                "not in" -> "NotIn"
                "is" -> "Is"
                "is not" -> "IsNot"
                "not" -> "NotIn"
                else -> throw NotImplementedError("Unsupported operation type")
            }
        }
        is PyPrefixExpression -> {
            when (this.operator.toString()) {
                "Py:MINUS" -> "USub"
                "Py:PLUS" -> "UAdd"
                "Py:NOT_KEYWORD" -> "Not"
                else -> throw NotImplementedError("Unsupported operation type")
            }
        }
        else -> {
            throw NotImplementedError("Unsupported operation this")
        }
    }

fun PySequenceExpression.getCollectionLabel(): String =
    when (this) {
        is PyListLiteralExpression -> "List"
        is PyTupleExpression -> "Tuple"
        is PySetLiteralExpression -> "Set"
        is PyDictLiteralExpression -> "Dict"
        else -> throw IllegalArgumentException()
    }

class GraphBuildingException(message: String) : Exception(message)