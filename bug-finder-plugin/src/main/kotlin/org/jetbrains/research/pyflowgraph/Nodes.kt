package org.jetbrains.research.pyflowgraph

import com.intellij.psi.PsiElement

typealias ControlBranchStack = MutableList<Pair<StatementNode?, Boolean>>

var statementCounter: Int = 0

abstract class Node(
    open var label: String,
    open var psi: PsiElement?,
    var statementNum: Int = statementCounter++
) {
    var unmappable: Boolean? = null
    var syntaxTokenIntervals: String? = null
    var defFor: MutableList<Int> = mutableListOf()
    var defBy: MutableList<Int> = mutableListOf()
    var defControlBranchStack: ControlBranchStack = mutableListOf()
    val inEdges = HashSet<Edge>()
    val outEdges = HashSet<Edge>()

    fun getDefinitions() = inEdges
        .filter { it is DataEdge && it.label == LinkType.REFERENCE }
        .map { it.nodeFrom }

    fun createEdge(nodeTo: Node, linkType: String, fromClosure: Boolean = false) {
        val edge = DataEdge(linkType, this, nodeTo, fromClosure)
        outEdges.add(edge)
        nodeTo.inEdges.add(edge)
    }

    fun hasInEdge(nodeFrom: Node, label: String) =
        inEdges.any { it.nodeFrom == nodeFrom && it.label == label }

    fun removeInEdge(edge: Edge) {
        inEdges.remove(edge)
        edge.nodeFrom.outEdges.remove(edge)
    }

    fun removeOutEdge(edge: Edge) {
        outEdges.remove(edge)
        edge.nodeTo.inEdges.remove(edge)
    }

    fun getIncomingNodes(label: String? = null) = inEdges
        .filter { label == null || it.label == label }
        .toSet()

    fun getOutgoingNodes(label: String? = null) = outEdges
        .filter { label == null || it.label == label }
        .toSet()

    override fun toString(): String {
        return "#$statementNum"
    }
}

class DataNode(
    override var label: String,
    override var psi: PsiElement?,
    val key: String? = null,
    var kind: String = Kind.UNDEFINED
) : Node(label, psi) {

    object Kind {
        const val VARIABLE_DECLARATION = "variable-decl"
        const val VARIABLE_USAGE = "variable-usage"
        const val SUBSCRIPT = "subscript"
        const val SLICE = "slice"
        const val LITERAL = "literal"
        const val KEYWORD = "keyword"
        const val UNDEFINED = "undefined"
    }

    override fun toString(): String {
        return "#$statementNum $label <$kind>"
    }
}

open class StatementNode(
    override var label: String,
    override var psi: PsiElement?,
    controlBranchStack: ControlBranchStack?
) : Node(label, psi) {

    open val controlBranchStack: ControlBranchStack? = controlBranchStack?.map { it.copy() }?.toMutableList()

    init {
        if (this !is EntryNode && controlBranchStack != null) {
            val (control, branchKind) = controlBranchStack.last()
            control?.createControlEdge(this, branchKind, addToStack = false)
        }
    }

    fun control() = controlBranchStack?.last()?.first

    fun branchKind() = controlBranchStack?.last()?.second

    private fun createControlEdge(
        nodeTo: StatementNode, branchKind: Boolean,
        addToStack: Boolean = true, fromClosure: Boolean = false
    ) {
        val controlEdge = ControlEdge(this, nodeTo, branchKind, fromClosure)
        outEdges.add(controlEdge)
        nodeTo.inEdges.add(controlEdge)
        if (addToStack) {
            nodeTo.controlBranchStack?.add(Pair(this, branchKind))
        }
    }

    fun resetControls() {
        inEdges.filterIsInstance<ControlEdge>().forEach { this.removeInEdge(it) }
        controlBranchStack?.clear()
    }
}

class EmptyNode(override var controlBranchStack: ControlBranchStack) :
    StatementNode("empty", null, controlBranchStack)

class OperationNode(
    override var label: String,
    override var psi: PsiElement?,
    override var controlBranchStack: ControlBranchStack,
    var key: String? = null,
    var kind: String = Kind.UNCLASSIFIED
) : StatementNode(label, psi, controlBranchStack) {

    object Label {
        const val RETURN = "return"
        const val CONTINUE = "continue"
        const val BREAK = "break"
        const val RAISE = "raise"
        const val PASS = "pass"
        const val LAMBDA = "lambda"
        const val ASSIGN = "="
    }

    object Kind {
        const val COLLECTION = "collection"
        const val FUNC_CALL = "func-call"
        const val ASSIGN = "assignment"
        const val AUG_ASSIGN = "aug-assignment"
        const val COMPARE = "comparison"
        const val RETURN = "return"
        const val RAISE = "raise"
        const val BREAK = "break"
        const val CONTINUE = "continue"
        const val UNARY = "unary"
        const val BOOL = "bool"
        const val BINARY = "binary"
        const val UNCLASSIFIED = "unclassified"
    }

    override fun toString(): String {
        return "#$statementNum $label <$kind>"
    }
}

open class ControlNode(
    override var label: String,
    override var psi: PsiElement?,
    override val controlBranchStack: ControlBranchStack
) : StatementNode(label, psi, controlBranchStack) {

    object Label {
        const val IF = "if"
        const val FOR = "for"
        const val WHILE = "while"
        const val TRY = "try"
        const val EXCEPT = "except"
    }

    override fun toString(): String {
        return "#$statementNum $label"
    }
}

class EntryNode(psi: PsiElement?) : ControlNode("START", psi, mutableListOf())