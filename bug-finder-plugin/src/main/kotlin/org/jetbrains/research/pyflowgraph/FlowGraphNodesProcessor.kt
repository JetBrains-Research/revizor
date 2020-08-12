package org.jetbrains.research.pyflowgraph

@ExperimentalStdlibApi
interface FlowGraphNodesProcessor {

    fun processFlowGraphNodes(
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

}