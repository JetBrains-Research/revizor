package org.jetbrains.research.pyflowgraph.postprocessing

import org.jetbrains.research.pyflowgraph.models.*


object TransitiveClosureBuilder : FlowGraphNodesProcessor {

    fun buildClosure(flowGraph: PyFlowGraph) {
        processFlowGraphNodes(flowGraph, this::buildDataClosure)
        processFlowGraphNodes(flowGraph, this::buildControlClosure)
        processFlowGraphNodes(flowGraph, this::buildControlDataClosure)
    }

    private fun buildDataClosure(node: Node, processedNodes: MutableSet<Node>) {
        if (node.getDefinitions().isNotEmpty()) {
            return
        }
        for (edge in node.inEdges.toHashSet()) {
            if (edge !is DataEdge) {
                continue
            }

            val inNodes = edge.nodeFrom.getDefinitions()
            if (inNodes.isEmpty()) {
                inNodes.add(edge.nodeFrom)
            } else {
                for (inNode in inNodes) {
                    if (!node.hasInEdge(inNode, edge.label)) {
                        inNode.createEdge(node, edge.label, fromClosure = true)
                    }
                }
            }

            for (inNode in inNodes) {
                if (!processedNodes.contains(inNode)) {
                    buildDataClosure(inNode, processedNodes)
                }
                for (inNodeEdge in inNode.inEdges) {
                    if (inNodeEdge is DataEdge
                        && inNodeEdge.nodeFrom !is DataNode
                        && !inNodeEdge.nodeFrom.hasInEdge(node, edge.label)
                    ) {
                        val afterInNode = inNodeEdge.nodeFrom
                        afterInNode.defFor = inNode.defFor
                        val defFor = afterInNode.defFor
                        if (edge.label == LinkType.DEFINITION
                            && (defFor.isEmpty() || !defFor.contains(node.statementNum))
                        ) {
                            continue
                        }
                        if (!node.hasInEdge(afterInNode, edge.label)) {
                            afterInNode.createEdge(node, edge.label, fromClosure = true)
                        }
                    }
                }
            }
        }
        processedNodes.add(node)
    }

    private fun buildControlClosure(node: Node, processedNodes: MutableSet<Node>) {
        if (node !is ControlNode) {
            return
        }
        for (inControl in node.getIncomingNodes(label = LinkType.CONTROL)) {
            if (!processedNodes.contains(inControl)) {
                buildControlClosure(inControl, processedNodes)
            }
            for (edge in inControl.inEdges) {
                if (edge !is ControlEdge) {
                    continue
                }
                val inControlFrom = edge.nodeFrom as StatementNode
                inControlFrom.createControlEdge(node, edge.branchKind, fromClosure = true)
            }
        }
        processedNodes.add(node)
    }

    private fun buildControlDataClosure(node: Node, processedNodes: MutableSet<Node>) {
        if (node !is StatementNode) {
            return
        }
        val nodeControls: List<StatementNode> = node.controlBranchStack?.mapNotNull { it.first } ?: listOf()
        for (edge in node.inEdges.toHashSet()) {
            val inNode = edge.nodeFrom
            if (edge !is ControlEdge || inNode !is ControlNode) {
                continue
            }
            if (!processedNodes.contains(inNode)) {
                buildControlDataClosure(inNode, processedNodes)
            }
            val visited = HashSet<Node>()
            for (inEdge in inNode.inEdges.toHashSet()) {
                val inNodeFrom = inEdge.nodeFrom
                if (inNodeFrom !is OperationNode && inNodeFrom !is ControlNode
                    || visited.contains(inNodeFrom)
                ) {
                    continue
                }
                var lowestControl: ControlNode? = null
                if (inNodeFrom is ControlNode) {
                    if (node is ControlNode) {
                        continue // already built
                    }
                    lowestControl = inNodeFrom
                } else {
                    for (control in inNodeFrom.getOutgoingNodes()) {
                        if (control !is ControlNode || !nodeControls.contains(control)) {
                            continue
                        }
                        if (lowestControl == null || control.statementNum < lowestControl.statementNum) {
                            lowestControl = control
                        }
                    }
                }
                var branchKind: Boolean?
                if (lowestControl == inNode) {
                    branchKind = edge.branchKind
                } else {
                    var inLowestEdge: ControlEdge? = null
                    for (lowestOutEdge in lowestControl?.outEdges ?: HashSet()) {
                        if (lowestOutEdge.label == LinkType.CONTROL && lowestOutEdge.nodeTo == inNode) {
                            inLowestEdge = lowestOutEdge as ControlEdge
                            break
                        }
                    }
                    branchKind = inLowestEdge?.branchKind
                }
                branchKind?.let {
                    (inNodeFrom as StatementNode).createControlEdge(
                        node,
                        it,
                        addToStack = false,
                        fromClosure = true
                    )
                }
                visited.add(inNodeFrom)
            }
        }
        processedNodes.add(node)
    }
}