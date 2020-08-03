package org.jetbrains.research.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.research.common.*
import org.jgrapht.graph.AsSubgraph

class BugFinderStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        Config.tempDirectory.mkdirs()
        val patternsGlobalDirs = HashSet<String>()
        Config.patternsOutputPath.toFile().walk().forEach {
            if (it.isFile.and(it.extension == "dot").and(it.name.startsWith("fragment"))) {
                if (!patternsGlobalDirs.contains(it.parent)) {
                    patternsGlobalDirs.add(it.parent)
                    val currentGraph = loadGraphFromDotFile(it)
                    val subgraphBefore = AsSubgraph<Vertex, MultipleEdge>(
                        currentGraph, currentGraph.vertexSet().filter { it.color == "red2" }.toSet()
                    )
                    PatternsState.patternsGraphs[it.toPath()] = subgraphBefore
                }
            }
        }
    }
}