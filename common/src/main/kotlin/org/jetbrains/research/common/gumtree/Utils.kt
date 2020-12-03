package org.jetbrains.research.common.gumtree

import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.actions.model.Insert
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.tree.ITree

fun getAllTreesFromActions(actions: Collection<Action>): List<ITree> {
    val result = arrayListOf<ITree>()
    for (action in actions) {
        result.add(action.node)
        if (action is Insert) {
            result.add(action.parent)
        } else if (action is Move) {
            result.add(action.parent)
        }
    }
    return result
}
