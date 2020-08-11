package org.jetbrains.research.pyflowgraph

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