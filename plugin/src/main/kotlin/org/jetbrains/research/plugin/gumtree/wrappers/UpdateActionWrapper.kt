package org.jetbrains.research.plugin.gumtree.wrappers

import com.github.gumtreediff.actions.model.Update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.jgrapht.findVertexById

@Serializable
@SerialName("Update")
class UpdateActionWrapper : ActionWrapper<Update> {
    private var targetVertexWrapper: PyPsiGumTreeWrapper
    private var value: String

    constructor(action: Update) {
        this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
        this.value = action.value
    }

    override fun reconstructAction(correspondingGraph: PatternGraph): Update {
        targetVertexWrapper.rootVertex = correspondingGraph.findVertexById(targetVertexWrapper.rootVertexId!!)
        return Update(targetVertexWrapper.getNode(), value)
    }
}