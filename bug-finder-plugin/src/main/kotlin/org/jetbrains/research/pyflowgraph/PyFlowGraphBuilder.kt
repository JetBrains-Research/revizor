package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*
import org.jetbrains.research.pyflowgraph.models.*
import org.jetbrains.research.pyflowgraph.postprocessing.DependenciesResolver
import org.jetbrains.research.pyflowgraph.postprocessing.TransitiveClosureBuilder


class PyFlowGraphBuilder {
    internal val controlBranchStack: ControlBranchStack = mutableListOf()
    private val flowGraph: PyFlowGraph = createGraph()
    private var currentControlNode: Node? = null
    private var currentBranchKind: Boolean = true
    private val contextStack: MutableList<BuildingContext> = mutableListOf(
        BuildingContext()
    )
    private val helper = PyAssignmentVisitorHelper(this)

    fun buildForPyFunction(
        node: PyFunction,
        showDependencies: Boolean = false,
        buildClosure: Boolean = true
    ): PyFlowGraph {
        val fg = visitPyElement(node) ?: throw GraphBuildingException
        if (!showDependencies) {
            DependenciesResolver.resolve(flowGraph = fg)
        }
        if (buildClosure) {
            TransitiveClosureBuilder.buildClosure(flowGraph = fg)
        }
        fg.makeConsistent()
        return fg
    }

    fun visitPyElement(node: PyElement?): PyFlowGraph? =
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
            is PyCallExpression -> visitCall(node)
            is PyExpressionStatement -> visitPyElement(node.expression)
            is PyIfStatement -> visitIfStatement(node)
            is PyForStatement -> visitFor(node)
            is PyWhileStatement -> visitWhile(node)
            is PyTryExceptStatement -> visitTry(node)
            is PyImportStatement -> visitImport()
            is PyFromImportStatement -> visitImportFrom()
            is PyBreakStatement -> visitBreak(node)
            is PyContinueStatement -> visitContinue(node)
            is PyRaiseStatement -> visitRaise(node)
            is PyReturnStatement -> visitReturn(node)
            is PyParenthesizedExpression -> visitPyElement(node.containedExpression)
            else -> null
        }

    fun createGraph(node: Node? = null) = PyFlowGraph(this, node)

    fun context() = contextStack.last()

    private fun switchContext(newContext: BuildingContext) = contextStack.add(newContext)

    private fun popContext() = contextStack.removeAt(contextStack.indices.last)

    private fun switchControlBranch(newControl: StatementNode, newBranchKind: Boolean, replace: Boolean = false) {
        if (replace) {
            popControlBranch()
        }
        controlBranchStack.add(Pair(newControl, newBranchKind))
    }

    private fun popControlBranch() {
        val lastElement = controlBranchStack.removeAt(controlBranchStack.indices.last)
        currentControlNode = lastElement.first
        currentBranchKind = lastElement.second
    }

    private fun visitFunction(node: PyFunction): PyFlowGraph {
        if (flowGraph.entryNode == null) {
            val entryNode = EntryNode(node)
            flowGraph.entryNode = entryNode
            switchControlBranch(entryNode, true)
            val argumentsFlowGraphs = visitFunctionDefParameters(node)
            flowGraph.parallelMergeGraphs(argumentsFlowGraphs)
            for (statement in node.statementList.statements) {
                val statementFlowGraph = try {
                    visitPyElement(statement)
                } catch (e: GraphBuildingException) {
                    null
                }
                if (statementFlowGraph == null) {
                    println("Unable to build pfg for $statement, skipping...")
                    continue
                }
                flowGraph.mergeGraph(statementFlowGraph)
            }
            popControlBranch()
            return flowGraph
        }
        return visitNonAssignmentVariableDeclaration(node)
    }

    private fun visitStrLiteral(node: PyStringLiteralExpression): PyFlowGraph =
        createGraph(node = DataNode(node.stringValue, node, kind = DataNode.Kind.LITERAL))

    private fun visitNumLiteral(node: PyNumericLiteralExpression): PyFlowGraph =
        createGraph(node = DataNode(node.bigIntegerValue.toString(), node, kind = DataNode.Kind.LITERAL))

    private fun visitBoolLiteral(node: PyBoolLiteralExpression): PyFlowGraph =
        createGraph(
            node = DataNode(
                if (node.value) {
                    "True"
                } else {
                    "False"
                }, node, kind = DataNode.Kind.LITERAL
            )
        )

    private fun visitNoneLiteral(node: PyNoneLiteralExpression): PyFlowGraph =
        createGraph(node = DataNode("None", node, kind = DataNode.Kind.LITERAL))

    private fun visitPass(node: PyPassStatement): PyFlowGraph =
        createGraph(node = OperationNode(OperationNode.Label.PASS, node, controlBranchStack))

    private fun visitLambda(node: PyLambdaExpression): PyFlowGraph {
        switchContext(context().fork())
        val currentFlowGraph = createGraph()
        val parametersFlowGraphs = visitFunctionDefParameters(node)
        currentFlowGraph.parallelMergeGraphs(parametersFlowGraphs)
        val lambdaNode = DataNode(OperationNode.Label.LAMBDA, node)
        val bodyFlowGraph = visitPyElement(node.body) ?: throw GraphBuildingException
        currentFlowGraph.mergeGraph(bodyFlowGraph)
        currentFlowGraph.addNode(lambdaNode, LinkType.PARAMETER, clearSinks = true)
        popContext()
        return currentFlowGraph
    }

    private fun visitList(node: PyListLiteralExpression): PyFlowGraph = visitCollectionHelper(node)

    private fun visitTuple(node: PyTupleExpression): PyFlowGraph = visitCollectionHelper(node)

    private fun visitSet(node: PySetLiteralExpression): PyFlowGraph = visitCollectionHelper(node)

    private fun visitDict(node: PyDictLiteralExpression): PyFlowGraph {
        val keyValueFlowGraphs = ArrayList<PyFlowGraph>()
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
            label = getCollectionLabel(node),
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.COLLECTION
        )
        currentFlowGraph.addNode(operationNode, LinkType.PARAMETER)
        return currentFlowGraph
    }

    private fun visitReference(node: PyReferenceExpression): PyFlowGraph {
        val referenceFullname = getNodeFullName(node)
        val referenceKey = getNodeKey(node)
        val referenceNode = DataNode(
            label = referenceFullname,
            psi = node,
            key = referenceKey,
            kind = DataNode.Kind.VARIABLE_USAGE
        )
        return if (node.isQualified) {
            val qualifierFlowGraph = visitPyElement(node.qualifier) ?: throw GraphBuildingException
            qualifierFlowGraph.addNode(referenceNode, linkType = LinkType.QUALIFIER, clearSinks = true)
            qualifierFlowGraph
        } else {
            val varUsageFlowGraph = createGraph()
            varUsageFlowGraph.addNode(referenceNode)
            varUsageFlowGraph
        }
    }

    private fun visitSubscript(node: PySubscriptionExpression): PyFlowGraph {
        val operandFlowGraph = visitPyElement(node.operand) ?: throw GraphBuildingException
        val indexFlowGraph = visitPyElement(node.indexExpression) ?: throw GraphBuildingException
        val subscriptFullName = getNodeFullName(node)
        val subscriptNode = DataNode(label = subscriptFullName, psi = node, kind = DataNode.Kind.SUBSCRIPT)
        return mergeOperandWithIndexGraphs(operandFlowGraph, indexFlowGraph, subscriptNode)
    }

    private fun visitSlice(node: PySliceExpression): PyFlowGraph {
        val operandFlowGraph = visitPyElement(node.operand) ?: throw GraphBuildingException
        val sliceItemsGraphs = arrayListOf<PyFlowGraph>()
        node.sliceItem?.lowerBound?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        node.sliceItem?.stride?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        node.sliceItem?.upperBound?.let { visitPyElement(it) }?.let { sliceItemsGraphs.add(it) }
        val sliceFlowGraph = createGraph()
        sliceFlowGraph.parallelMergeGraphs(sliceItemsGraphs)
        val sliceFullName = getNodeFullName(node)
        val sliceNode = DataNode(label = sliceFullName, psi = node, kind = DataNode.Kind.SUBSCRIPT)
        return mergeOperandWithIndexGraphs(operandFlowGraph, sliceFlowGraph, sliceNode)
    }

    private fun visitBinaryOperation(node: PyBinaryExpression): PyFlowGraph {
        return if (node.isOperator("and") || node.isOperator("or")) {
            visitBinOperationHelper(
                operationName = getOperationName(node),
                node = node,
                operationKind = OperationNode.Kind.BOOL,
                leftOperand = node.leftExpression,
                rightOperand = node.rightExpression ?: throw GraphBuildingException
            )
        } else if (node.isOperator("!=") || node.isOperator("==") || node.isOperator("<")
            || node.isOperator("<=") || node.isOperator(">") || node.isOperator(">")
        ) {
            visitBinOperationHelper(
                operationName = getOperationName(node),
                node = node,
                operationKind = OperationNode.Kind.COMPARE,
                leftOperand = node.leftExpression,
                rightOperand = node.rightExpression ?: throw GraphBuildingException
            )
        } else {
            visitBinOperationHelper(
                operationName = getOperationName(node),
                node = node,
                operationKind = OperationNode.Kind.BINARY,
                leftOperand = node.leftExpression,
                rightOperand = node.rightExpression ?: throw GraphBuildingException
            )
        }
    }

    private fun visitPrefixOperation(node: PyPrefixExpression): PyFlowGraph {
        val params = ArrayList<PyExpression>()
        node.operand?.let { params.add(it) }
        return visitOperationHelper(
            operationName = getOperationName(node),
            node = node,
            operationKind = OperationNode.Kind.UNARY,
            params = params
        )
    }

    private fun visitAssign(node: PyAssignmentStatement): PyFlowGraph =
        helper.visitAssign(node, node.rawTargets)

    private fun visitAugAssign(node: PyAugAssignmentStatement): PyFlowGraph {
        // TODO: fix non-null asserted calls
        if (node.target is PyReferenceExpression && node.value != null && node.operation != null) {
            val valueFlowGraph = visitBinOperationHelper(
                node = node,
                leftOperand = node.target,
                rightOperand = node.value!!,
                operationName = getOperationName(node),
                operationKind = OperationNode.Kind.AUG_ASSIGN
            )
            return visitSimpleAssignment(
                target = node.target,
                preparedValue = PyAssignmentVisitorHelper.PreparedAssignmentValue.AssignedValue(valueFlowGraph)
            )
        } else {
            throw GraphBuildingException
        }
    }

    private fun visitSimpleAssignment(
        target: PyElement,
        preparedValue: PyAssignmentVisitorHelper.PreparedAssignmentValue
    ): PyFlowGraph {
        val operationNode = OperationNode(
            label = OperationNode.Label.ASSIGN,
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

    private fun visitNonAssignmentVariableDeclaration(node: PyElement): PyFlowGraph {
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

    private fun visitFunctionDefParameters(node: PyCallable): List<PyFlowGraph> {
        val parametersFlowGraphs = ArrayList<PyFlowGraph?>()
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

    private fun visitCall(node: PyCallExpression): PyFlowGraph {
        val name = node.callee?.let { getNodeShortName(it) } ?: throw GraphBuildingException
        val key = node.callee?.let { getNodeKey(it) } ?: throw GraphBuildingException
        return if (node.callee is PyQualifiedExpression && (node.callee as PyQualifiedExpression).isQualified) {
            val calleeFlowGraph = node.callee?.let { visitPyElement(it) } ?: throw GraphBuildingException
            val callFlowGraph = visitFunctionCallHelper(node, name)
            calleeFlowGraph.mergeGraph(
                callFlowGraph,
                linkNode = callFlowGraph.sinks.first(),
                linkType = LinkType.RECEIVER
            )
            calleeFlowGraph
        } else {
            visitFunctionCallHelper(node, name, key)
        }
    }

    private fun visitIfStatement(node: PyIfStatement): PyFlowGraph {
        val controlNode = ControlNode(ControlNode.Label.IF, node, controlBranchStack)
        val ifFlowGraph = visitPyElement(node.ifPart.condition)
        ifFlowGraph?.addNode(controlNode, LinkType.CONDITION)
        val bodyFlowGraph = visitControlNodeBodyHelper(
            controlNode,
            node.ifPart.statementList.statements,
            newBranchKind = true
        )
        val elseFlowGraph = visitElifPartsHelper(
            globalIfControlNode = controlNode,
            prevControlNode = controlNode,
            elifParts = node.elifParts.toList(),
            elsePart = node.elsePart
        )
        ifFlowGraph?.parallelMergeGraphs(listOf(bodyFlowGraph, elseFlowGraph))
        return ifFlowGraph ?: throw GraphBuildingException
    }

    private fun visitElifPartsHelper(
        globalIfControlNode: ControlNode,
        prevControlNode: ControlNode,
        elifParts: List<PyIfPart>,
        elsePart: PyElsePart?
    ): PyFlowGraph {
        if (elifParts.isNotEmpty()) {
            val ifPart = elifParts.first()

            // Emulating _visit_control_node_body call for nested elif-s each time
            switchControlBranch(prevControlNode, true)
            val controlWrapperFlowGraph = createGraph()
            controlWrapperFlowGraph.addNode(EmptyNode(controlBranchStack))

            val controlNode = ControlNode(ControlNode.Label.IF, ifPart, controlBranchStack)
            val ifPartFlowGraph = visitPyElement(ifPart.condition)
            ifPartFlowGraph?.addNode(controlNode, LinkType.CONDITION)
            val bodyFlowGraph = visitControlNodeBodyHelper(
                controlNode,
                ifPart.statementList.statements,
                newBranchKind = true
            )
            val elseFlowGraph =
                if (elifParts.size > 1) {
                    visitElifPartsHelper(
                        globalIfControlNode,
                        controlNode,
                        elifParts.subList(1, elifParts.size),
                        elsePart
                    )
                } else {
                    visitControlNodeBodyHelper(
                        controlNode,
                        elsePart?.statementList?.statements ?: arrayOf(),
                        newBranchKind = false
                    )
                }
            ifPartFlowGraph?.parallelMergeGraphs(listOf(bodyFlowGraph, elseFlowGraph))

            // Emulating _visit_control_node_body call for nested elif-s each time
            ifPartFlowGraph?.let { controlWrapperFlowGraph.mergeGraph(it) } ?: throw GraphBuildingException
            popControlBranch()

            return controlWrapperFlowGraph
        } else {
            return visitControlNodeBodyHelper(
                globalIfControlNode,
                elsePart?.statementList?.statements ?: arrayOf(),
                newBranchKind = false
            )
        }
    }

    private fun visitFor(node: PyForStatement): PyFlowGraph {
        val controlNode = ControlNode(ControlNode.Label.FOR, node, controlBranchStack)
        val forFlowGraph = visitSimpleAssignment(
            target = node.forPart.target ?: throw GraphBuildingException,
            preparedValue = helper.prepareAssignmentValues(node.forPart.target, node.forPart.source)
        )
        forFlowGraph.addNode(controlNode, LinkType.CONDITION)
        val forBodyFlowGraph = visitControlNodeBodyHelper(
            controlNode = controlNode,
            statements = node.forPart.statementList.statements,
            newBranchKind = true
        )
        // TODO: it's easy to add `else` part, but it's not parsed in original miner
        forFlowGraph.parallelMergeGraphs(listOf(forBodyFlowGraph))
        forFlowGraph.statementSinks.clear()
        return forFlowGraph
    }

    private fun visitWhile(node: PyWhileStatement): PyFlowGraph {
        val controlNode = ControlNode(ControlNode.Label.WHILE, node, controlBranchStack)
        val whileFlowGraph = visitPyElement(node.whilePart.condition)
        whileFlowGraph?.addNode(controlNode, LinkType.CONDITION)
        val whileBodyFlowGraph = visitControlNodeBodyHelper(
            controlNode = controlNode,
            statements = node.whilePart.statementList.statements,
            newBranchKind = true
        )
        // TODO: it's easy to add `else` part, but it's not parsed in original miner
        whileFlowGraph?.parallelMergeGraphs(listOf(whileBodyFlowGraph))
        whileFlowGraph?.statementSinks?.clear()
        return whileFlowGraph ?: throw GraphBuildingException
    }

    private fun visitTry(node: PyTryExceptStatement): PyFlowGraph {
        val controlNode = ControlNode(ControlNode.Label.TRY, node, controlBranchStack)
        val tryFlowGraph = createGraph()
        tryFlowGraph.addNode(controlNode, LinkType.CONDITION)
        val tryBodyFlowGraph = visitControlNodeBodyHelper(
            controlNode = controlNode,
            statements = node.tryPart.statementList.statements,
            newBranchKind = true
        )
        switchControlBranch(controlNode, newBranchKind = false)
        val exceptHandlersFlowGraphs = node.exceptParts.map { visitExcept(it) }
        tryFlowGraph.parallelMergeGraphs(listOf(tryBodyFlowGraph).plus(exceptHandlersFlowGraphs))
        popControlBranch()
        tryFlowGraph.statementSinks.clear()
        return tryFlowGraph
    }

    private fun visitExcept(node: PyExceptPart): PyFlowGraph {
        val controlNode = ControlNode(ControlNode.Label.EXCEPT, node, controlBranchStack)
        // TODO: include node.target definition in original miner
        val exceptFlowGraph = visitPyElement(node.exceptClass) ?: createGraph()
        exceptFlowGraph.addNode(controlNode, LinkType.PARAMETER)
        exceptFlowGraph.mergeGraph(
            visitControlNodeBodyHelper(
                controlNode,
                node.statementList.statements,
                newBranchKind = true
            )
        )
        exceptFlowGraph.statementSources.clear() // TODO: what means "bad workaround"?
        return exceptFlowGraph
    }

    // TODO: fix import stubs in original miner
    private fun visitImport() = createGraph()

    private fun visitImportFrom() = createGraph()

    private fun visitRaise(node: PyRaiseStatement): PyFlowGraph =
        visitOperationAndResetHelper(
            OperationNode.Label.RAISE,
            node,
            OperationNode.Kind.RAISE,
            resetVariables = true
        )

    private fun visitReturn(node: PyReturnStatement): PyFlowGraph =
        visitOperationAndResetHelper(
            OperationNode.Label.RETURN,
            node,
            OperationNode.Kind.RETURN,
            resetVariables = true
        )

    private fun visitContinue(node: PyContinueStatement): PyFlowGraph =
        visitOperationAndResetHelper(
            OperationNode.Label.CONTINUE,
            node,
            OperationNode.Kind.CONTINUE,
            resetVariables = false
        )

    private fun visitBreak(node: PyBreakStatement): PyFlowGraph =
        visitOperationAndResetHelper(
            OperationNode.Label.BREAK,
            node,
            OperationNode.Kind.BREAK,
            resetVariables = false
        )

    private fun visitFunctionCallHelper(
        node: PyCallExpression,
        name: String,
        key: String? = null
    ): PyFlowGraph {
        val argumentsFlowGraphs = visitFunctionCallArgumentsHelper(node)
        val callFlowGraph = createGraph()
        callFlowGraph.parallelMergeGraphs(argumentsFlowGraphs)
        val operationNode = OperationNode(
            label = name,
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = OperationNode.Kind.FUNC_CALL,
            key = key
        )
        callFlowGraph.addNode(operationNode, LinkType.PARAMETER, clearSinks = true)
        return callFlowGraph
    }

    private fun visitFunctionCallArgumentsHelper(node: PyCallExpression): List<PyFlowGraph> {
        val argsFlowGraphs = ArrayList<PyFlowGraph?>()
        for (arg in node.arguments) {
            if (arg is PyKeywordArgument) {
                val argFlowGraph = visitPyElement(arg.valueExpression)
                val keyWordNode = DataNode(arg.keyword ?: "keyword", arg, kind = DataNode.Kind.KEYWORD)
                argFlowGraph?.addNode(keyWordNode)
                argsFlowGraphs.add(argFlowGraph)
            } else {
                argsFlowGraphs.add(visitPyElement(arg))
            }
        }
        if (argsFlowGraphs.any { it == null }) {
            val calleeShortName = node.callee?.let { getNodeShortName(it) }
            println("Function call <$calleeShortName> has unsupported arguments, skipping them...")
        }
        return argsFlowGraphs.filterNotNull()
    }

    private fun visitControlNodeBodyHelper(
        controlNode: ControlNode,
        statements: Array<PyStatement>,
        newBranchKind: Boolean,
        replaceControl: Boolean = false
    ): PyFlowGraph {
        switchControlBranch(controlNode, newBranchKind, replaceControl)
        val currentFlowGraph = createGraph()
        currentFlowGraph.addNode(EmptyNode(controlBranchStack))
        for (statement in statements) {
            var statementFlowGraph: PyFlowGraph? = null
            try {
                statementFlowGraph = visitPyElement(statement)
            } finally {
                if (statementFlowGraph == null) {
                    println("Unable to build graph for $statement inside ${controlNode.psi}, skipping...")
                    continue
                }
                currentFlowGraph.mergeGraph(statementFlowGraph)
            }
        }
        popControlBranch()
        return currentFlowGraph
    }

    private fun visitOperationAndResetHelper(
        label: String,
        node: PyElement,
        kind: String,
        resetVariables: Boolean = false
    ): PyFlowGraph {
        val fg = createGraph()
        when (node) {
            is PyReturnStatement -> visitPyElement(node.expression)?.let { fg.mergeGraph(it) }
            // TODO: why there is only one expression in raise from original ast?
            is PyRaiseStatement -> visitPyElement(node.expressions.firstOrNull())?.let { fg.mergeGraph(it) }
        }
        val operationNode = OperationNode(label, node, controlBranchStack, kind = kind)
        fg.addNode(operationNode, LinkType.PARAMETER)
        fg.statementSinks.clear()
        if (resetVariables) {
            context().removeVariables(controlBranchStack)
        }
        return fg
    }

    private fun visitCollectionHelper(node: PySequenceExpression): PyFlowGraph {
        val currentFlowGraph = createGraph()
        currentFlowGraph.parallelMergeGraphs(node.elements.mapNotNull { visitPyElement(it) })
        val operationNode = OperationNode(
            label = getCollectionLabel(node),
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
    ): PyFlowGraph {
        val operationNode = OperationNode(
            label = operationName?.toLowerCase()?.trim('_') ?: "unknown",
            psi = node,
            controlBranchStack = controlBranchStack,
            kind = operationKind
        )
        val paramsFlowGraphs: ArrayList<PyFlowGraph> = ArrayList()
        params.forEach { param ->
            visitPyElement(param).also {
                it?.addNode(operationNode, LinkType.PARAMETER)
                if (it != null) {
                    paramsFlowGraphs.add(it)
                }
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
    ): PyFlowGraph {
        return visitOperationHelper(
            operationName = operationName ?: "unknown",
            node = node,
            operationKind = operationKind,
            params = arrayListOf(leftOperand, rightOperand)
        )
    }

    private fun mergeOperandWithIndexGraphs(
        operandFlowGraph: PyFlowGraph,
        indexFlowGraph: PyFlowGraph,
        subscriptNode: DataNode
    ): PyFlowGraph {
        indexFlowGraph.addNode(subscriptNode, linkType = LinkType.PARAMETER, clearSinks = true)
        operandFlowGraph.mergeGraph(
            indexFlowGraph,
            linkNode = indexFlowGraph.sinks.first(),
            linkType = LinkType.QUALIFIER
        )
        return operandFlowGraph
    }

}
