package org.jetbrains.research.plugin.gumtree.wrappers

import org.jetbrains.research.plugin.PatternGraph

interface ActionWrapper<out T> {
    fun reconstructAction(correspondingGraph: PatternGraph): T
}