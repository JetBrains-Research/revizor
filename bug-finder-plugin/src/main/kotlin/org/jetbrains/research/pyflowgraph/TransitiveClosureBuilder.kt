package org.jetbrains.research.pyflowgraph

@ExperimentalStdlibApi
object TransitiveClosureBuilder : FlowGraphNodesProcessor {

    fun buildClosure(flowGraph: ExtControlFlowGraph) {
        processFlowGraphNodes(flowGraph, this::buildDataClosure)
        processFlowGraphNodes(flowGraph, this::buildControlClosure)
        processFlowGraphNodes(flowGraph, this::buildControlDataClosure)
    }

    private fun buildDataClosure(node: Node, processedNodes: MutableSet<Node>): Unit {
        TODO()
    }

    private fun buildControlClosure(node: Node, processedNodes: MutableSet<Node>): Unit {
        TODO()
    }

    private fun buildControlDataClosure(node: Node, processedNodes: MutableSet<Node>): Unit {
        TODO()
    }
}