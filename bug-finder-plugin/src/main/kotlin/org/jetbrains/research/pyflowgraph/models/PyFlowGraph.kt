package org.jetbrains.research.pyflowgraph.models

import org.jetbrains.research.pyflowgraph.GraphBuildingException
import org.jetbrains.research.pyflowgraph.PyFlowGraphBuilder


class PyFlowGraph(
    private val builder: PyFlowGraphBuilder,
    val node: Node? = null
) {
    var entryNode: EntryNode? = null
        set(node) {
            if (entryNode != null) {
                throw GraphBuildingException
            }
            if (node != null) {
                field = node
                nodes.add(node)
            }
        }
    val nodes: MutableSet<Node> = mutableSetOf()
    private val operationNodes: MutableSet<OperationNode> = mutableSetOf()
    private val variableReferences: MutableSet<Node> = mutableSetOf()
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

    private fun resolveReferences(graph: PyFlowGraph): MutableSet<Node> {
        val resolvedReferences = mutableSetOf<Node>()
        for (refNode in graph.variableReferences) {
            val defNodes = refNode.key?.let { builder.context().getVariables(it) }
            if (defNodes != null) {
                defNodes.filter { it.statementNum < refNode.statementNum }
                    .forEach { it.createEdge(refNode, LinkType.REFERENCE) }
                resolvedReferences.add(refNode)
            }
        }
        return resolvedReferences
    }

    fun mergeGraph(graph: PyFlowGraph, linkNode: Node? = null, linkType: String? = null) {
        if (linkNode != null && linkType != null) {
            for (sink in sinks) {
                sink.createEdge(linkNode, linkType)
            }
        }
        val resolvedReferences = resolveReferences(graph)
        val unresolvedReferences = graph.variableReferences.minus(resolvedReferences)
        for (sink in statementSinks) {
            for (source in graph.statementSources) {
                sink.createEdge(source, LinkType.DEPENDENCE)
            }
        }
        nodes.addAll(graph.nodes)
        operationNodes.addAll(graph.operationNodes)
        sinks = graph.sinks
        statementSinks = graph.statementSinks
        variableReferences.addAll(unresolvedReferences)
    }

    fun parallelMergeGraphs(graphs: List<PyFlowGraph>, operationLinkType: String? = null) {
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
        if (node.key != null && linkType != LinkType.DEFINITION) {
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

    fun makeConsistent() {
        for (node in nodes) {
            node.outEdges.removeIf { !nodes.contains(it.nodeTo) }
            node.inEdges.removeIf { !nodes.contains(it.nodeFrom) }
        }
    }
}
