package org.jetbrains.research.pyflowgraph

import com.jetbrains.python.psi.*

fun getNodeFullName(node: PyElement): String{
    TODO("Not yet implemented")
}

fun getNodeKey(node: PyElement): String? {
    if (node is PyFunction) {
        return node.name
    }
    TODO("Not yet implemented")
}