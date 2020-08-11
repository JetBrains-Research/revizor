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

open class Edge(
    var label: String,
    var nodeFrom: Node,
    var nodeTo: Node,
    var fromClosure: Boolean = false
) {
    override fun toString(): String {
        return "$nodeFrom =$label> $nodeTo"
    }
}

class ControlEdge(nodeFrom: Node, nodeTo: Node, var branchKind: Boolean = true, fromClosure: Boolean = false) :
    Edge(LinkType.CONTROL, nodeFrom, nodeTo, fromClosure) {
    override fun toString(): String {
        return "$nodeFrom =$label> $nodeTo [$branchKind]"
    }
}

class DataEdge(label: String, nodeFrom: Node, nodeTo: Node, fromClosure: Boolean) :
    Edge(label, nodeFrom, nodeTo, fromClosure)

object LinkType {
    const val DEFINITION = "def"
    const val RECEIVER = "recv"
    const val REFERENCE = "ref"
    const val PARAMETER = "para"
    const val CONDITION = "cond"
    const val QUALIFIER = "qual"
    const val CONTROL = "control"
    const val DEPENDENCE = "dep"
}

@ExperimentalStdlibApi
class ExtControlFlowGraph(
    val visitor: PyFlowGraphBuilder,
    val node: Node? = null
) {
    var entryNode: EntryNode? = null
        set(node) {
            if (entryNode != null) {
                throw DuplicateEntryNodeException
            }
            if (node != null) {
                field = node
                nodes.add(node)
            }
        }
    val nodes: MutableSet<Node> = mutableSetOf()
    val operationNodes: MutableSet<OperationNode> = mutableSetOf()
    val variableReferences: MutableSet<DataNode> = mutableSetOf()
    var sinks: MutableSet<Node> = mutableSetOf()
    var statementSinks: MutableSet<StatementNode> = mutableSetOf()
    val statementSources: MutableSet<StatementNode> = mutableSetOf()

    init {
        if (node != null) {
            nodes.add(node)
            sinks.add(node)
            if (node is StatementNode) {
                statementSinks.add(node)
                statementSources.add(node)
            }
            if (node is OperationNode) {
                operationNodes.add(node)
            }
        }
    }

    fun resolveReferences(graph: ExtControlFlowGraph): MutableSet<DataNode> {
        val resolvedReferences = mutableSetOf<DataNode>()
        for (refNode in graph.variableReferences) {
            val defNodes = refNode.key?.let { visitor.context().getVariables(it) }
            if (defNodes != null) {
                defNodes
                    .filter { it.statementNum < refNode.statementNum }
                    .forEach { it.createEdge(refNode, LinkType.REFERENCE) }
                resolvedReferences.add(refNode)
            }
        }
        return resolvedReferences
    }

    fun mergeGraph(graph: ExtControlFlowGraph, linkNode: Node? = null, linkType: String? = null) {
        if (linkNode != null && linkType != null) {
            for (sink in sinks) {
                sink.createEdge(linkNode, linkType)
            }
        }
        val resolvedReferences = resolveReferences(graph)
        val unresolvedReferences = graph.variableReferences.minus(resolvedReferences)
        for (sink in statementSinks) {
            for (source in graph.statementSources) {
                sink.createEdge(source, LinkType.REFERENCE)
            }
        }
        nodes.addAll(graph.nodes)
        operationNodes.addAll(graph.operationNodes)
        sinks = graph.sinks
        statementSinks = graph.statementSinks
        variableReferences.addAll(unresolvedReferences)
    }

    fun parallelMergeGraphs(graphs: List<ExtControlFlowGraph>, operationLinkType: String? = null) {
        val oldSinks = sinks.toMutableSet()
        val oldStatementSinks = statementSinks.toMutableSet()
        sinks.clear()
        statementSinks.clear()
        for (graph in graphs) {
            val resolvedReferences = resolveReferences(graph)
            val unresolvedReferences = graph.variableReferences.minus(resolvedReferences)
            if (operationLinkType != null) {
                for (operationNode in graph.operationNodes) {
                    for (sink in oldSinks) {
                        if (!operationNode.hasInEdge(sink, operationLinkType)) {
                            sink.createEdge(operationNode, operationLinkType)
                        }
                    }
                }
            }
            for (sink in oldStatementSinks) {
                for (source in graph.statementSources) {
                    sink.createEdge(source, LinkType.DEPENDENCE)
                }
            }
            nodes.addAll(graph.nodes)
            operationNodes.addAll(graph.operationNodes)
            sinks.addAll(graph.sinks)
            variableReferences.addAll(unresolvedReferences)
            statementSinks.addAll(graph.statementSinks)
        }
    }

    fun addNode(node: Node, linkType: String? = null, clearSinks: Boolean = false) {
        if (linkType != null) {
            sinks.forEach { it.createEdge(node, linkType) }
        }
        if ((node is DataNode) && linkType != LinkType.DEFINITION) {
            variableReferences.add(node)
        }
        if (clearSinks) {
            sinks.clear()
        }
        sinks.add(node)
        nodes.add(node)
        if (node is StatementNode) {
            statementSinks.forEach { it.createEdge(node, LinkType.DEPENDENCE) }
            statementSinks.clear()
            statementSinks.add(node)
            if (statementSources.isEmpty()) {
                statementSources.add(node)
            }
        }
        if (node is OperationNode) {
            operationNodes.add(node)
        }
    }

    fun removeNode(node: Node) {
        node.inEdges.forEach { it.nodeFrom.outEdges.remove(it) }
        node.outEdges.forEach { it.nodeTo.inEdges.remove(it) }
        node.inEdges.clear()
        node.outEdges.clear()
        nodes.remove(node)
        operationNodes.remove(node)
        sinks.remove(node)
        statementSinks.remove(node)
    }

    fun findFirstNodeByLabel(label: String) = nodes.find { it.label == label }
}

object DuplicateEntryNodeException : Throwable()
