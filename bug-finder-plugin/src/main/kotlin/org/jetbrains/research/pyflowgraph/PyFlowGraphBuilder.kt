package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

@ExperimentalStdlibApi
class PyFlowGraphBuilder {
    private val flowGraph: ExtControlFlowGraph = createGraph()
    var currentControlNode: Node? = null
    var currentBranchKind: Boolean = true
    val controlBranchStack: ControlBranchStack = mutableListOf()
    private val contextStack: MutableList<BuildingContext> = mutableListOf(BuildingContext())
    private val helper = PyFlowGraphBuilderHelper(this)

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

    private fun visitOperationHelper(
        operationName: String?,
        node: PyElement,
        operationKind: String,
        params: List<PyExpression>
    ): ExtControlFlowGraph {
        val operationNode = OperationNode(
            label = operationName ?: "unknownOp",
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

    private fun visitBinOperationHelper(
        node: PyElement,
        leftOperand: PyExpression,
        rightOperand: PyExpression,
        operationName: String? = null,
        operationKind: String = OperationNode.Kind.UNCLASSIFIED
    ): ExtControlFlowGraph {
        return visitOperationHelper(
            operationName = operationName ?: "unknownBinOp",
            node = node,
            operationKind = operationKind,
            params = arrayListOf(leftOperand, rightOperand)
        )
    }

    private fun visitFunction(node: PyFunction): ExtControlFlowGraph {
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

    private fun visitStrLiteral(node: PyStringLiteralExpression): ExtControlFlowGraph =
        createGraph(node = DataNode(node.stringValue, node, kind = DataNode.Kind.LITERAL))

    private fun visitNumLiteral(node: PyNumericLiteralExpression): ExtControlFlowGraph =
        createGraph(node = DataNode(node.bigIntegerValue.toString(), node, kind = DataNode.Kind.LITERAL))

    private fun visitBoolLiteral(node: PyBoolLiteralExpression): ExtControlFlowGraph =
        createGraph(
            node = DataNode(
                if (node.value) {
                    "True"
                } else {
                    "False"
                }, node, kind = DataNode.Kind.LITERAL
            )
        )

    private fun visitNoneLiteral(node: PyNoneLiteralExpression): ExtControlFlowGraph =
        createGraph(node = DataNode("None", node, kind = DataNode.Kind.LITERAL))

    private fun visitPass(node: PyPassStatement): ExtControlFlowGraph =
        createGraph(node = OperationNode(OperationNode.Label.PASS.name, node, controlBranchStack))

    private fun visitLambda(node: PyLambdaExpression): ExtControlFlowGraph {
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

    private fun visitList(node: PyListLiteralExpression): ExtControlFlowGraph = visitCollection(node)

    private fun visitTuple(node: PyTupleExpression): ExtControlFlowGraph = visitCollection(node)

    private fun visitSet(node: PySetLiteralExpression): ExtControlFlowGraph = visitCollection(node)

    private fun visitDict(node: PyDictLiteralExpression): ExtControlFlowGraph {
        val keyValueFlowGraphs = ArrayList<ExtControlFlowGraph>()
        for (element in node.elements) {
            keyValueFlowGraphs.add(
                visitBinOperationHelper(
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

    private fun visitReference(node: PyReferenceExpression): ExtControlFlowGraph {
        val referenceFullname = getNodeFullName(node)
        val referenceKey = getNodeKey(node)
        val referenceNode = DataNode(
            label = referenceFullname,
            psi = node,
            key = referenceKey,
            kind = DataNode.Kind.VARIABLE_USAGE
        )
        return if (node.isQualified) {
            val qualifierFlowGraph = visitPyElement(node.qualifier)
            qualifierFlowGraph.addNode(referenceNode, linkType = LinkType.QUALIFIER, clearSinks = true)
            qualifierFlowGraph
        } else {
            val varUsageFlowGraph = createGraph()
            varUsageFlowGraph.addNode(referenceNode)
            varUsageFlowGraph
        }
    }

    private fun visitSubscript(node: PySubscriptionExpression): ExtControlFlowGraph {
        val operandFlowGraph = visitPyElement(node.operand)
        val indexFlowGraph = visitPyElement(node.indexExpression)
        val subscriptFullName = getNodeFullName(node)
        val subscriptNode = DataNode(label = subscriptFullName, psi = node, kind = DataNode.Kind.SUBSCRIPT)
        return mergeOperandWithIndexGraphs(operandFlowGraph, indexFlowGraph, subscriptNode)
    }

    private fun visitSlice(node: PySliceExpression): ExtControlFlowGraph {
        val operandFlowGraph = visitPyElement(node.operand)
        val sliceItemsGraphs = arrayListOf<ExtControlFlowGraph>()
        node.sliceItem?.lowerBound?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        node.sliceItem?.stride?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        node.sliceItem?.upperBound?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        val sliceFlowGraph = createGraph()
        sliceFlowGraph.parallelMergeGraphs(sliceItemsGraphs)
        val sliceFullName = getNodeFullName(node)
        val sliceNode = DataNode(label = sliceFullName, psi = node, kind = DataNode.Kind.SUBSCRIPT)
        return mergeOperandWithIndexGraphs(operandFlowGraph, sliceFlowGraph, sliceNode)
    }

    private fun mergeOperandWithIndexGraphs(
        operandFlowGraph: ExtControlFlowGraph,
        indexFlowGraph: ExtControlFlowGraph,
        subscriptNode: DataNode
    ): ExtControlFlowGraph {
        indexFlowGraph.addNode(subscriptNode, linkType = LinkType.PARAMETER, clearSinks = true)
        operandFlowGraph.mergeGraph(
            indexFlowGraph,
            linkNode = indexFlowGraph.sinks.first(),
            linkType = LinkType.QUALIFIER
        )
        return operandFlowGraph
    }

    private fun visitBinaryOperation(node: PyBinaryExpression): ExtControlFlowGraph {
        val params = arrayListOf(node.leftExpression)
        node.rightExpression?.let { params.add(it) }
        return if (node.isOperator("and") || node.isOperator("or")) {
            visitOperationHelper(
                operationName = node.operator?.specialMethodName,
                node = node,
                operationKind = OperationNode.Kind.BOOL,
                params = params
            )
        } else {
            visitOperationHelper(
                operationName = node.operator?.specialMethodName,
                node = node,
                operationKind = OperationNode.Kind.BINARY,
                params = params
            )
        }
    }

    private fun visitPrefixOperation(node: PyPrefixExpression): ExtControlFlowGraph {
        val params = ArrayList<PyExpression>()
        node.operand?.let { params.add(it) }
        return visitOperationHelper(
            operationName = node.operator.specialMethodName,
            node = node,
            operationKind = OperationNode.Kind.UNARY,
            params = params
        )
    }

    private fun visitAssign(node: PyAssignmentStatement): ExtControlFlowGraph =
        helper.visitAssign(node, node.rawTargets)

    private fun visitAugAssign(node: PyAugAssignmentStatement): ExtControlFlowGraph {
        // TODO: fix non-null asserted calls
        if (node is PyReferenceExpression && node.value != null && node.operation != null) {
            val valueFlowGraph = visitBinOperationHelper(
                node = psiToPyOperation(node.operation!!),
                leftOperand = node.target,
                rightOperand = node.value!!,
                operationKind = OperationNode.Kind.AUG_ASSIGN
            )
            return visitSimpleAssignment(
                target = node.target,
                preparedValue = PyFlowGraphBuilderHelper.PreparedAssignmentValue.AssignedValue(valueFlowGraph)
            )
        } else {
            throw GraphBuildingException
        }
    }

    private fun visitSimpleAssignment(
        target: PyElement,
        preparedValue: PyFlowGraphBuilderHelper.PreparedAssignmentValue
    ): ExtControlFlowGraph {
        val operationNode = OperationNode(
            label = OperationNode.Label.ASSIGN.name,
            psi = target,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.ASSIGN
        )
        val (fg, vars) = helper.getAssignFlowGraphWithVars(operationNode, target, preparedValue)
        for (varNode in vars) {
            context().addVariable(varNode)
        }
        return fg
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
                val preparedValue = helper.prepareAssignmentValues(parameter, parameter.defaultValue!!)
                parametersFlowGraphs.add(visitSimpleAssignment(parameter, preparedValue))
            } else {
                parametersFlowGraphs.add(visitNonAssignmentVariableDeclaration(parameter))
            }
        }
        if (parametersFlowGraphs.any { it == null }) {
            println("Unsupported parameters in function definition, skipping them...")
        }
        return parametersFlowGraphs.filterNotNull()
    }

    fun visitPyElement(node: PyElement?): ExtControlFlowGraph =
        when (node) {
            is PyFunction -> visitFunction(node)
            is PyReferenceExpression -> visitReference(node)
            is PyBinaryExpression -> visitBinaryOperation(node)
            is PyPrefixExpression -> visitPrefixOperation(node)
            is PyTupleExpression -> visitTuple(node)
            is PyListLiteralExpression -> visitList(node)
            is PySetLiteralExpression -> visitSet(node)
            is PyDictLiteralExpression -> visitDict(node)
            is PyNumericLiteralExpression -> visitNumLiteral(node)
            is PyStringLiteralExpression -> visitStrLiteral(node)
            is PyBoolLiteralExpression -> visitBoolLiteral(node)
            is PyNoneLiteralExpression -> visitNoneLiteral(node)
            is PyPassStatement -> visitPass(node)
            is PyLambdaExpression -> visitLambda(node)
            is PyAssignmentStatement -> visitAssign(node)
            is PyAugAssignmentStatement -> visitAugAssign(node)
            is PySubscriptionExpression -> visitSubscript(node)
            is PySliceExpression -> visitSlice(node)
            else -> createGraph()
        }
}
