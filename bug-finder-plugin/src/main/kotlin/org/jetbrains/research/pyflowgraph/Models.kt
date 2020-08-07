package org.jetbrains.research.pyflowgraph

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

typealias ControlBranchStack = MutableList<Pair<StatementNode?, Boolean>>

var statementCounter: Int = 0

abstract class Node(
    open var label: String,
    open var psi: PsiElement?,
    var statementNum: Int = statementCounter++
) {
    enum class Property(repr: String) {
        UNMAPPABLE("unmappable"),
        SYNTAX_TOKEN_INTERVALS("syntax-tokens"),
        DEF_FOR("def-for"),
        DEF_BY("def-by"),
        DEF_CONTROL_BRANCH_STACK("def-stack")
    }

    var unmappable: Boolean? = null
    var syntaxTokenIntervals: String? = null
    val defFor: MutableList<Node> = mutableListOf()
    val defBy: MutableList<Node> = mutableListOf()
    val defControlBranchStack: ControlBranchStack = mutableListOf()
    val inEdges = HashSet<Edge>()
    val outEdges = HashSet<Edge>()

    fun getDefinitions() = inEdges
        .filter { it is DataEdge && it.label == LinkType.REFERENCE }
        .map { it.nodeFrom }

    fun createEdge(nodeTo: Node, linkType: LinkType, fromClosure: Boolean = false) {
        val edge = DataEdge(linkType, this, nodeTo, fromClosure)
        outEdges.add(edge)
        nodeTo.inEdges.add(edge)
    }

    fun hasInEdge(nodeFrom: Node, label: LinkType) =
        inEdges.any { it.nodeFrom == nodeFrom && it.label == label }

    fun removeInEdge(edge: Edge) {
        inEdges.remove(edge)
        edge.nodeFrom.outEdges.remove(edge)
    }

    fun removeOutEdge(edge: Edge) {
        outEdges.remove(edge)
        edge.nodeTo.inEdges.remove(edge)
    }

    fun getIncomingNodes(label: LinkType? = null) = inEdges
        .filter { label == null || it.label == label }
        .toSet()

    fun getOutgoingNodes(label: LinkType? = null) = outEdges
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
    var kind: Kind = Kind.UNDEFINED
) : Node(label, psi) {

    enum class Kind(repr: String) {
        VARIABLE_DECLARATION("variable-decl"),
        VARIABLE_USAGE("variable-usage"),
        SUBSCRIPT("subscript"),
        SLICE("slice"),
        LITERAL("literal"),
        KEYWORD("keyword"),
        UNDEFINED("undefined")
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
        if (!(this is EntryNode) && controlBranchStack != null) {
            val (control, branchKind) = controlBranchStack.last()
            control?.createControlEdge(this, branchKind, addToStack = false)
        }
    }

    fun control() = controlBranchStack?.last()?.first

    fun branchKind() = controlBranchStack?.last()?.second

    fun createControlEdge(
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
    var kind: Kind = Kind.UNCLASSIFIED
) : StatementNode(label, psi, controlBranchStack) {

    enum class Label(repr: String) {
        RETURN("return"),
        CONTINUE("continue"),
        BREAK("break"),
        RAISE("raise"),
        PASS("pass"),
        LAMBDA("lambda"),
        ASSIGN("=")
    }

    enum class Kind(repr: String) {
        COLLECTION("collection"),
        FUNC_CALL("func-call"),
        ASSIGN("assignment"),
        AUG_ASSIGN("aug-assignment"),
        COMPARE("comparison"),
        RETURN("return"),
        RAISE("raise"),
        BREAK("break"),
        CONTINUE("continue"),
        UNARY("unary"),
        BOOL("bool"),
        BINARY("binary"),
        UNCLASSIFIED("unclassified")
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

    enum class Label(repr: String) {
        IF("if"),
        FOR("for"),
        WHILE("while"),
        TRY("try"),
        EXCEPT("except")
    }

    override fun toString(): String {
        return "#$statementNum $label"
    }
}

class EntryNode(psi: PsiElement?) : ControlNode("START", psi, mutableListOf())

open class Edge(
    var label: LinkType,
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

class DataEdge(label: LinkType, nodeFrom: Node, nodeTo: Node, fromClosure: Boolean) :
    Edge(label, nodeFrom, nodeTo, fromClosure)

enum class LinkType(repr: String) {
    DEFINITION("def"),
    RECEIVER("recv"),
    REFERENCE("ref"),
    PARAMETER("para"),
    CONDITION("cond"),
    QUALIFIER("qual"),
    CONTROL("control"),
    DEPENDENCE("dep")
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
                entryNode = node
                nodes.add(node)
            }
        }
    val nodes: MutableSet<Node> = mutableSetOf()
    val operationNodes: MutableSet<OperationNode> = mutableSetOf()
    val variableReferences: MutableSet<Node> = mutableSetOf()
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

    fun resolveReferences(graph: ExtControlFlowGraph): MutableSet<Node> {
        val resolvedReferences = mutableSetOf<Node>()
        for (refNode in graph.variableReferences) {
            val defNodes: Set<Node> = TODO()
            if (defNodes != null) {
                defNodes
                    .filter { it.statementNum < refNode.statementNum }
                    .forEach { it.createEdge(refNode, LinkType.REFERENCE) }
                resolvedReferences.add(refNode)
            }
        }
        return resolvedReferences
    }

    fun mergeGraph(graph: ExtControlFlowGraph, linkNode: Node? = null, linkType: LinkType? = null) {
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

    fun parallelMergeGraphs(graphs: List<ExtControlFlowGraph>, operationLinkType: LinkType? = null) {
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

    fun addNode(node: Node, linkType: LinkType? = null, clearSinks: Boolean = false) {
        if (linkType != null) {
            sinks.forEach { it.createEdge(node, linkType) }
        }
        if (node.javaClass.kotlin.members.any { it.name == "key" } && linkType != LinkType.DEFINITION) {
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
