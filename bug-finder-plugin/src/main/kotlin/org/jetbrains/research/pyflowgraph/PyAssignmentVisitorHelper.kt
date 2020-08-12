package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

@ExperimentalStdlibApi
internal class PyAssignmentVisitorHelper(private val builder: PyFlowGraphBuilder) {

    // Helps to emulate dynamically-typed lists from python
    // TODO: fix it later
    sealed class PreparedAssignmentValue {
        data class AssignedValue(val value: ExtControlFlowGraph) : PreparedAssignmentValue()
        data class AssignedValues(val values: MutableList<PreparedAssignmentValue>) : PreparedAssignmentValue()
    }

    fun visitAssign(node: PyAssignmentStatement, targets: Array<PyExpression>): ExtControlFlowGraph {
        val operationNode = OperationNode(
            label = OperationNode.Label.ASSIGN,
            psi = node,
            controlBranchStack = builder.controlBranchStack,
            kind = OperationNode.Kind.ASSIGN
        )
        val fgs = ArrayList<ExtControlFlowGraph>()
        val assignedNodes = ArrayList<DataNode>()
        for (target in targets) {
            val preparedValueGraphs = prepareAssignmentValues(target, node.assignedValue)
            val (currentAssignmentFlowGraph, variableNodes) = getAssignFlowGraphWithVars(
                operationNode,
                target,
                preparedValueGraphs
            )
            assignedNodes.addAll(variableNodes)
            fgs.add(currentAssignmentFlowGraph)
        }
        val assignmentFlowGraph = createGraph()
        assignmentFlowGraph.parallelMergeGraphs(fgs)
        assignedNodes.forEach { builder.context().addVariable(it) }
        return assignmentFlowGraph
    }

    private fun createGraph() = builder.createGraph()

    private fun getAssignGroup(target: PyElement): List<PyExpression> {
        val group = ArrayList<PyExpression>()
        when (target) {
            is PyTargetExpression -> group.add(target)
            is PyTupleExpression, is PyListLiteralExpression -> {
                (target as PySequenceExpression).elements.forEach { group.addAll(getAssignGroup(it)) }
            }
            is PyStarExpression -> target.expression?.let { group.add(it) }
            else -> throw GraphBuildingException
        }
        return group
    }

    internal fun getAssignFlowGraphWithVars(
        operationNode: OperationNode,
        target: PyElement,
        preparedValueGraphs: PreparedAssignmentValue
    ): Pair<ExtControlFlowGraph, List<DataNode>> {
        when (target) {
            is PyTargetExpression, is PyNamedParameter, is PyReferenceExpression -> {
                val name = getNodeFullName(target)
                val key = getNodeKey(target)
                val assignmentFlowGraph = (preparedValueGraphs as PreparedAssignmentValue.AssignedValue).value
                val varNode = DataNode(name, target, key, DataNode.Kind.VARIABLE_DECLARATION)

                val sinkNums = ArrayList<Int>()
                for (sink in assignmentFlowGraph.sinks) {
                    sink.defFor = mutableListOf(varNode.statementNum)
                    sinkNums.add(sink.statementNum)
                }
                varNode.defBy = sinkNums

                assignmentFlowGraph.addNode(operationNode, LinkType.PARAMETER)
                assignmentFlowGraph.addNode(varNode, LinkType.DEFINITION)
                varNode.defControlBranchStack = operationNode.controlBranchStack

                return Pair(assignmentFlowGraph, listOf(varNode))
            }
            is PyTupleExpression, is PyListLiteralExpression -> {
                val varNodes = ArrayList<DataNode>()
                when (preparedValueGraphs) {
                    is PreparedAssignmentValue.AssignedValue -> {
                        val preparedValueGraph = preparedValueGraphs.value
                        val assignGroup = getAssignGroup(target)
                        val fgs = ArrayList<ExtControlFlowGraph>()
                        for (element in assignGroup) {
                            val (fg, currentVarNodes) = getAssignFlowGraphWithVars(
                                operationNode, element, PreparedAssignmentValue.AssignedValue(createGraph())
                            )
                            fgs.add(fg)
                            varNodes.addAll(currentVarNodes)
                        }
                        preparedValueGraph.parallelMergeGraphs(fgs, operationLinkType = LinkType.PARAMETER)
                        return Pair(preparedValueGraph, varNodes)
                    }
                    is PreparedAssignmentValue.AssignedValues -> {
                        val tempFlowGraph = createGraph()
                        val tempFlowGraphs = ArrayList<ExtControlFlowGraph>()
                        for ((i, element) in (target as PySequenceExpression).elements.withIndex()) {
                            val (fg, currentVars) = getAssignFlowGraphWithVars(
                                operationNode,
                                element,
                                preparedValueGraphs.values[i]
                            )
                            tempFlowGraphs.add(fg)
                            varNodes.addAll(currentVars)
                        }
                        tempFlowGraph.parallelMergeGraphs(tempFlowGraphs)
                        return Pair(tempFlowGraph, varNodes)
                    }
                }
            }
            is PyStarExpression -> return target.expression?.let {
                getAssignFlowGraphWithVars(
                    operationNode, it, preparedValueGraphs
                )
            } ?: throw GraphBuildingException
            is PyParenthesizedExpression -> return target.containedExpression?.let {
                getAssignFlowGraphWithVars(operationNode, it, preparedValueGraphs)
            } ?: throw GraphBuildingException
            else -> throw GraphBuildingException
        }
    }

    internal fun prepareAssignmentValues(
        target: PyElement?,
        value: PyExpression?
    ): PreparedAssignmentValue {
        when (target) {
            is PyTargetExpression, is PyNamedParameter ->
                return PreparedAssignmentValue.AssignedValue(
                    builder.visitPyElement(value) ?: throw GraphBuildingException
                )
            is PyParenthesizedExpression ->
                return prepareAssignmentValues(target.containedExpression, value)
            is PyTupleExpression, is PyListLiteralExpression -> {
                when (value) {
                    is PyCallExpression, is PyReferenceExpression ->
                        return PreparedAssignmentValue.AssignedValue(
                            builder.visitPyElement(value) ?: throw GraphBuildingException
                        )
                    is PyListLiteralExpression, is PyTupleExpression -> {
                        val preparedSubAssignments = PreparedAssignmentValue.AssignedValues(mutableListOf())
                        val valueElements = (value as PySequenceExpression).elements.toList()
                        var targetStarredIndex: Int? = null
                        for ((i, subTarget) in (target as PySequenceExpression).elements.withIndex()) {
                            if (subTarget is PyStarExpression) {
                                targetStarredIndex = i
                                break
                            }
                            preparedSubAssignments.values.add(
                                prepareAssignmentValues(target = subTarget, value = valueElements[i])
                            )
                        }
                        if (targetStarredIndex == null) {
                            return preparedSubAssignments
                        } else {
                            val starredValueElementsCnt = valueElements.size - target.elements.size + 1
                            var starredValueElementsList = valueElements.subList(
                                fromIndex = targetStarredIndex,
                                toIndex = targetStarredIndex + starredValueElementsCnt
                            )
                            if (starredValueElementsList.isNotEmpty()
                                && starredValueElementsList.first() is PySequenceExpression
                            ) {
                                // TODO: wtf?
                                starredValueElementsList =
                                    (starredValueElementsList.first() as PySequenceExpression).elements.toList()
                            }
                            val operationNode = OperationNode(
                                label = "List",
                                psi = target,
                                controlBranchStack = builder.controlBranchStack,
                                kind = OperationNode.Kind.COLLECTION
                            )
                            val starredFlowGraph = createGraph()
                            starredFlowGraph.parallelMergeGraphs(
                                starredValueElementsList.mapNotNull { builder.visitPyElement(it) }
                            )
                            starredFlowGraph.addNode(operationNode, LinkType.PARAMETER)
                            preparedSubAssignments.values.add(
                                PreparedAssignmentValue.AssignedValue(starredFlowGraph)
                            )
                            val restValueElements =
                                valueElements.subList(targetStarredIndex + starredValueElementsCnt, valueElements.size)
                            for ((i, elem) in restValueElements.withIndex()) {
                                preparedSubAssignments.values.add(
                                    prepareAssignmentValues(
                                        target = target.elements[targetStarredIndex + i + 1],
                                        value = elem
                                    )
                                )
                            }
                            return preparedSubAssignments
                        }
                    }
                    else -> throw GraphBuildingException
                }
            }
            else -> throw GraphBuildingException
        }
    }
}