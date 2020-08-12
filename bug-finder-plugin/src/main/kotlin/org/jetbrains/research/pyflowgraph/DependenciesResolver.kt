package org.jetbrains.research.pyflowgraph

@ExperimentalStdlibApi
object DependenciesResolver {
    fun resolve(flowGraph: ExtControlFlowGraph) {
        processFlowGraphNodes(flowGraph, processorFunction = this::adjustControls)
        removeEmptyNodes(flowGraph)
        cleanUpDependencies(flowGraph)
    }

    private fun removeEmptyNodes(flowGraph: ExtControlFlowGraph) =
        flowGraph.nodes.toMutableSet()
            .filterIsInstance<EmptyNode>()
            .forEach { flowGraph.removeNode(it) }

    private fun cleanUpDependencies(flowGraph: ExtControlFlowGraph) {
        flowGraph.nodes.forEach { node ->
            node.inEdges
                .filter { it.label == LinkType.DEPENDENCE }
                .forEach { node.removeInEdge(it) }
        }
    }

    private fun processFlowGraphNodes(
        flowGraph: ExtControlFlowGraph,
        processorFunction: (node: Node, processedNodes: MutableSet<Node>) -> Unit
    ) {
        val processedNodes = hashSetOf<Node>()
        for (node in flowGraph.nodes) {
            if (!processedNodes.contains(node)) {
                processorFunction(node, processedNodes)
            }
        }
    }

    private fun adjustControls(node: Node, processedNodes: MutableSet<Node>) {
        if (node !is StatementNode) {
            return
        }
        val incomingDependencies = node.getIncomingNodes(label = LinkType.DEPENDENCE)
        if (incomingDependencies.isEmpty()) {
            processedNodes.add(node)
            return
        }
        val controlBranchStacks = ArrayList<ControlBranchStack>()
        val controlNodeToBranchKinds = mutableMapOf<StatementNode, MutableSet<Boolean>>()
        for (incomingDependency in incomingDependencies) {
            if (incomingDependency is ControlNode) {
                processedNodes.add(node)
                return
            }
            if (!processedNodes.contains(incomingDependency)) {
                adjustControls(incomingDependency, processedNodes)
            }
            (incomingDependency as StatementNode).controlBranchStack?.let { controlBranchStacks.add(it) }
        }
        node.controlBranchStack?.let { controlBranchStacks.add(it) }
        for (stack in controlBranchStacks) {
            for ((controlNode, branchKind) in stack) {
                if (controlNode != null) {
                    controlNodeToBranchKinds.getOrPut(controlNode) { HashSet() }.add(branchKind)
                }
            }
        }
        var deepestControlNode: StatementNode? = null
        var branchKind: Boolean? = null
        for ((controlNode, branchKinds) in controlNodeToBranchKinds) {
            if (branchKinds.singleOrNull() != null) {
                if (deepestControlNode == null || deepestControlNode.statementNum < controlNode.statementNum) {
                    deepestControlNode = controlNode
                    branchKind = branchKinds.single()
                }
            }
        }
        if (deepestControlNode != null) {
            node.resetControls()
            node.controlBranchStack = deepestControlNode.controlBranchStack?.toMutableList()
            deepestControlNode.createControlEdge(node, branchKind!!)
        }
        processedNodes.add(node)
    }

}