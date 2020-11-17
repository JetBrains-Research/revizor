package org.jetbrains.research.plugin.gumtree.wrappers

import com.github.gumtreediff.actions.model.Move
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.jgrapht.findVertexById

@Serializable
@SerialName("Move")
class MoveActionWrapper : ActionWrapper<Move> {
    private var targetVertexWrapper: PyPsiGumTreeWrapper
    private var parentVertexWrapper: PyPsiGumTreeWrapper?
    private var position: Int

    constructor(action: Move) {
        this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
        this.parentVertexWrapper = PyPsiGumTreeWrapper(action.parent as PyPsiGumTree)
        this.position = action.position
    }

    override fun reconstructAction(correspondingGraph: PatternGraph): Move {
        targetVertexWrapper.rootVertex = correspondingGraph.findVertexById(targetVertexWrapper.rootVertexId!!)
        parentVertexWrapper?.rootVertex = correspondingGraph.findVertexById(parentVertexWrapper?.rootVertexId!!)
        return Move(targetVertexWrapper.getNode(), parentVertexWrapper?.getNode(), position)
    }
}