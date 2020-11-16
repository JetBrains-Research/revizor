package org.jetbrains.research.plugin.gumtree.wrappers

import com.github.gumtreediff.actions.model.Delete
import kotlinx.serialization.Serializable
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.jgrapht.findVertexById

@Serializable
class DeleteActionWrapper : ActionWrapper<Delete> {
    private var targetVertexWrapper: PyPsiGumTreeWrapper

    constructor(action: Delete) {
        this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
    }

    override fun reconstructAction(correspondingGraph: PatternGraph): Delete {
        targetVertexWrapper.rootVertex = correspondingGraph.findVertexById(targetVertexWrapper.rootVertex?.id!!)
        return Delete(targetVertexWrapper.getNode())
    }
}