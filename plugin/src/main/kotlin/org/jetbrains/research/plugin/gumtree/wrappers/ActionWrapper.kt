package org.jetbrains.research.plugin.gumtree.wrappers

import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Insert
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.jgrapht.findVertexById

@Serializable
sealed class ActionWrapper {

    @Serializable
    @SerialName("Delete")
    class DeleteActionWrapper : ActionWrapper {
        private var targetVertexWrapper: PyPsiGumTreeWrapper

        constructor(action: Delete) : super() {
            this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
        }

        fun reconstructAction(correspondingDirectedAcyclicGraph: PatternDirectedAcyclicGraph): Delete {
            targetVertexWrapper.rootVertex = targetVertexWrapper.rootVertexId?.let {
                correspondingDirectedAcyclicGraph.findVertexById(it)
            }
            return Delete(targetVertexWrapper.getNode())
        }
    }

    @Serializable
    @SerialName("Update")
    class UpdateActionWrapper : ActionWrapper {
        private var targetVertexWrapper: PyPsiGumTreeWrapper
        private var value: String

        constructor(action: Update) : super() {
            this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.value = action.value
        }

        fun reconstructAction(correspondingDirectedAcyclicGraph: PatternDirectedAcyclicGraph): Update {
            targetVertexWrapper.rootVertex = targetVertexWrapper.rootVertexId?.let {
                correspondingDirectedAcyclicGraph.findVertexById(it)
            }
            return Update(targetVertexWrapper.getNode(), value)
        }
    }

    @Serializable
    @SerialName("Insert")
    class InsertActionWrapper : ActionWrapper {
        private var targetVertexWrapper: PyPsiGumTreeWrapper
        private var parentVertexWrapper: PyPsiGumTreeWrapper?
        private var position: Int

        constructor(action: Insert) : super() {
            this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.parentVertexWrapper = PyPsiGumTreeWrapper(action.parent as PyPsiGumTree)
            this.position = action.position
        }

        fun reconstructAction(correspondingDirectedAcyclicGraph: PatternDirectedAcyclicGraph): Insert {
            targetVertexWrapper.rootVertex = null
            parentVertexWrapper?.rootVertex = parentVertexWrapper?.rootVertexId?.let {
                correspondingDirectedAcyclicGraph.findVertexById(it)
            }
            return Insert(targetVertexWrapper.getNode(), parentVertexWrapper?.getNode(), position)
        }
    }

    @Serializable
    @SerialName("Move")
    class MoveActionWrapper : ActionWrapper {
        private var targetVertexWrapper: PyPsiGumTreeWrapper
        private var parentVertexWrapper: PyPsiGumTreeWrapper?
        private var position: Int

        constructor(action: Move) : super() {
            this.targetVertexWrapper = PyPsiGumTreeWrapper(action.node as PyPsiGumTree)
            this.parentVertexWrapper = PyPsiGumTreeWrapper(action.parent as PyPsiGumTree)
            this.position = action.position
        }

        fun reconstructAction(correspondingDirectedAcyclicGraph: PatternDirectedAcyclicGraph): Move {
            targetVertexWrapper.rootVertex = targetVertexWrapper.rootVertexId?.let {
                correspondingDirectedAcyclicGraph.findVertexById(it)
            }
            parentVertexWrapper?.rootVertex = parentVertexWrapper?.rootVertexId?.let {
                correspondingDirectedAcyclicGraph.findVertexById(it)
            }
            return Move(targetVertexWrapper.getNode(), parentVertexWrapper?.getNode(), position)
        }
    }
}