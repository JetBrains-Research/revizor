package org.jetbrains.research.preprocessing.models

import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import java.util.*

class EditActions(val actions: List<Action>) : Iterable<Action> {
    val size get() = actions.size

    fun sort() {
        val updates = arrayListOf<Pair<Int, Update>>()
        for ((i, action) in actions.withIndex()) {
            if (action is Update) {
                updates.add(Pair(i, action))
                continue
            }
            if (action is Move) {
                val item = updates.find { it.second.node.hasSameTypeAndLabel(action.node) } ?: continue
                Collections.swap(actions, i, item.first)
            }
        }
    }

    override fun iterator(): Iterator<Action> {
        return actions.iterator()
    }
}