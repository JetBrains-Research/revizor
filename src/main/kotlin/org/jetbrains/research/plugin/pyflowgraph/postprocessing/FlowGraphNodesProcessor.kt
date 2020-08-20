package org.jetbrains.research.plugin.pyflowgraph.postprocessing

import org.jetbrains.research.plugin.pyflowgraph.models.Node
import org.jetbrains.research.plugin.pyflowgraph.models.PyFlowGraph


interface FlowGraphNodesProcessor {

    fun processFlowGraphNodes(
        flowGraph: PyFlowGraph,
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