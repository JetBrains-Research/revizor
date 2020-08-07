package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

class BuildingContext {
    private val variableKeyToDefNodes: MutableMap<String, MutableSet<DataNode>> = mutableMapOf()

    fun fork() {
        val newContext = BuildingContext()
        for (entry in variableKeyToDefNodes) {
            newContext.variableKeyToDefNodes[entry.key] = entry.value.toMutableSet()
        }
    }

    fun addVariable(node: DataNode) {
        if (node.key != null) {
            val defNodes = variableKeyToDefNodes.getOrPut(node.key) { mutableSetOf() }
            for (defNode in defNodes.filter { it.key == node.key }) {
                val defNodeStack = defNode.defControlBranchStack
                val nodeStack = node.defControlBranchStack
                if (nodeStack.size <= defNodeStack.size
                    && defNodeStack.subList(0, nodeStack.size) == nodeStack
                ) {
                    defNodes.remove(defNode)
                }
            }
            defNodes.add(node)
            variableKeyToDefNodes[node.key] = defNodes
        }
    }

    fun removeVariables(controlBranchStack: ControlBranchStack) {
        variableKeyToDefNodes.values.forEach { defNodes ->
            defNodes
                .filter { it.defControlBranchStack == controlBranchStack }
                .forEach { defNodes.remove(it) }
        }
    }

    fun getVariables(variableKey: String) = variableKeyToDefNodes[variableKey]
}

@ExperimentalStdlibApi
class PyFlowGraphBuilder {
    val flowGraph: ExtControlFlowGraph = createGraph()
    var currentControlNode: Node? = null
    var currentBranchKind: Boolean = true
    val controlBranchStack: ControlBranchStack = mutableListOf()
    val contextStack: MutableList<BuildingContext> = mutableListOf(BuildingContext())

    fun createGraph(node: Node? = null) = ExtControlFlowGraph(this, node)

    fun context() = contextStack.last()

    private fun switchContext(newContext: BuildingContext) = contextStack.add(newContext)

    private fun popContext() = contextStack.removeLast()

    private fun switchControlBranch(newControl: StatementNode, newBranchKind: Boolean, replace: Boolean = false) {
        if (replace) {
            popControlBranch()
        }
        controlBranchStack.add(Pair(newControl, newBranchKind))
    }

    private fun popControlBranch() {
        val lastElement = controlBranchStack.removeLast()
        currentControlNode = lastElement.first
        currentBranchKind = lastElement.second
    }

    private fun visitCollection(node: PySequenceExpression): ExtControlFlowGraph {
        val flowGraph = createGraph()
        flowGraph.parallelMergeGraphs(node.elements.map { visitPyElement(it) })
        val operationNode = OperationNode(
            label = node.javaClass.name,
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.COLLECTION
        )
        flowGraph.addNode(operationNode, LinkType.PARAMETER)
        return flowGraph
    }

    fun visitOperation(
        operationName: String,
        node: PyElement,
        operationKind: OperationNode.Kind,
        params: List<PyExpression>
    ): ExtControlFlowGraph {
        val operationNode = OperationNode(
            label = operationName,
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = operationKind
        )
        val paramsFlowGraphs: ArrayList<ExtControlFlowGraph> = ArrayList()
        params.forEach { param ->
            visitPyElement(param).also {
                it.addNode(operationNode, LinkType.PARAMETER)
                paramsFlowGraphs.add(it)
            }
        }
        return createGraph().also { it.parallelMergeGraphs(paramsFlowGraphs) }
    }

    fun visitBinaryOperation(
        node: PyElement,
        leftOperand: PyExpression,
        rightOperand: PyExpression,
        operationName: String? = null,
        operationKind: OperationNode.Kind = OperationNode.Kind.UNCLASSIFIED
    ): ExtControlFlowGraph {
        return visitOperation(
            operationName = operationName ?: node.javaClass.name,
            node = node,
            operationKind = operationKind,
            params = arrayListOf(leftOperand, rightOperand)
        )
    }

    fun visitPyElement(node: PyElement?): ExtControlFlowGraph {
        node?.children
            ?.filterIsInstance<PyElement>()
            ?.forEach { visitPyElement(it) }
        return createGraph()
    }
}