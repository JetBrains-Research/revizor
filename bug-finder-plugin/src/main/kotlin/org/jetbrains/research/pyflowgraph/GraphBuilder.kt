package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

class BuildingContext {
    private val variableKeyToDefNodes: MutableMap<String, MutableSet<DataNode>> = mutableMapOf()

    fun fork(): BuildingContext {
        val newContext = BuildingContext()
        for (entry in variableKeyToDefNodes) {
            newContext.variableKeyToDefNodes[entry.key] = entry.value.toMutableSet()
        }
        return newContext
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
        val currentFlowGraph = createGraph()
        currentFlowGraph.parallelMergeGraphs(node.elements.map { visitPyElement(it) })
        val operationNode = OperationNode(
            label = node.name ?: "collection",
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.COLLECTION
        )
        currentFlowGraph.addNode(operationNode, LinkType.PARAMETER)
        return currentFlowGraph
    }

    private fun visitOperation(
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

    private fun visitBinaryOperation(
        node: PyElement,
        leftOperand: PyExpression,
        rightOperand: PyExpression,
        operationName: String? = null,
        operationKind: OperationNode.Kind = OperationNode.Kind.UNCLASSIFIED
    ): ExtControlFlowGraph {
        return visitOperation(
            operationName = operationName ?: node.name ?: "binOp",
            node = node,
            operationKind = operationKind,
            params = arrayListOf(leftOperand, rightOperand)
        )
    }

    fun visitFunction(node: PyFunction): ExtControlFlowGraph {
        if (flowGraph.entryNode == null) {
            val entryNode = EntryNode(node)
            flowGraph.entryNode = entryNode
            switchControlBranch(entryNode, true)
            val argumentsFlowGraphs = visitFunctionDefParameters(node)
            flowGraph.parallelMergeGraphs(argumentsFlowGraphs)
            for (statement in node.statementList.statements) {
                val statementFlowGraph = try {
                    visitPyElement(statement)
                } catch (e: Exception) {
                    null
                }
                if (statementFlowGraph == null) {
                    print("Unable to build pfg for $statement, skipping...")
                    continue
                }
                flowGraph.mergeGraph(statementFlowGraph)
            }
            popControlBranch()
            return flowGraph
        }
        return visitNonAssignmentVariableDeclaration(node)
    }

    fun visitStr(node: PyStringLiteralExpression): ExtControlFlowGraph =
        createGraph(node = DataNode(node.stringValue, node, kind = DataNode.Kind.LITERAL))

    fun visitNum(node: PyNumericLiteralExpression): ExtControlFlowGraph =
        createGraph(node = DataNode(node.bigIntegerValue.toString(), node, kind = DataNode.Kind.LITERAL))

    fun visitPass(node: PyPassStatement): ExtControlFlowGraph =
        createGraph(node = OperationNode(OperationNode.Label.PASS.name, node, controlBranchStack))

    fun visitLambda(node: PyLambdaExpression): ExtControlFlowGraph {
        switchContext(context().fork())
        val currentFlowGraph = createGraph()
        val parametersFlowGraphs = visitFunctionDefParameters(node)
        currentFlowGraph.parallelMergeGraphs(parametersFlowGraphs)
        val lambdaNode = DataNode(OperationNode.Label.LAMBDA.name, node)
        val bodyFlowGraph = visitPyElement(node.body)
        currentFlowGraph.mergeGraph(bodyFlowGraph)
        currentFlowGraph.addNode(lambdaNode, LinkType.PARAMETER, clearSinks = true)
        popContext()
        return currentFlowGraph
    }

    fun visitList(node: PyListLiteralExpression): ExtControlFlowGraph = visitCollection(node)

    fun visitTuple(node: PyTupleExpression): ExtControlFlowGraph = visitCollection(node)

    fun visitSet(node: PySetLiteralExpression): ExtControlFlowGraph = visitCollection(node)

    fun visitDict(node: PyDictLiteralExpression): ExtControlFlowGraph {
        val keyValueFlowGraphs = ArrayList<ExtControlFlowGraph>()
        for (element in node.elements) {
            keyValueFlowGraphs.add(
                visitBinaryOperation(
                    node,
                    element.key,
                    element.value!!, // TODO: Check why element.value can be null
                    "Key-Value"
                )
            )
        }
        val currentFlowGraph = createGraph()
        currentFlowGraph.parallelMergeGraphs(keyValueFlowGraphs)
        val operationNode = OperationNode(
            label = "Dict",
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.COLLECTION
        )
        currentFlowGraph.addNode(operationNode, LinkType.PARAMETER)
        return currentFlowGraph
    }

    private fun visitNonAssignmentVariableDeclaration(node: PyElement): ExtControlFlowGraph {
        val variableFullName = getNodeFullName(node)
        val variableKey = getNodeKey(node)
        val variableNode = DataNode(
            label = variableFullName,
            psi = node,
            key = variableKey,
            kind = DataNode.Kind.VARIABLE_DECLARATION
        )
        variableNode.defControlBranchStack = controlBranchStack
        context().addVariable(variableNode)
        return createGraph(variableNode)
    }

    private fun visitFunctionDefParameters(node: PyCallable): List<ExtControlFlowGraph> {
        val parametersFlowGraphs = ArrayList<ExtControlFlowGraph?>()
        for (parameter in node.parameterList.parameters) {
            if (parameter.hasDefaultValue()) {
                parametersFlowGraphs.add(visitSimpleAssignment(parameter, parameter.defaultValue!!))
            } else {
                parametersFlowGraphs.add(visitNonAssignmentVariableDeclaration(parameter))
            }
        }
        if (parametersFlowGraphs.any { it == null }) {
            println("Unsupported parameters in function definition, skipping them...")
        }
        return parametersFlowGraphs.filterNotNull()
    }

    private fun visitSimpleAssignment(target: PyElement, value: PyExpression): ExtControlFlowGraph {
        TODO("Not yet implemented")
    }

    fun visitPyElement(node: PyElement?): ExtControlFlowGraph {
        TODO("There would come generic visits, not yet implemented")
    }
}