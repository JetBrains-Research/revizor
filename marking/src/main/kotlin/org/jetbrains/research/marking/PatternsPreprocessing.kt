package org.jetbrains.research.marking

import com.google.gson.Gson
import org.jetbrains.research.plugin.Config
import org.jetbrains.research.plugin.jgrapht.PatternSpecificGraphsLoader
import org.jetbrains.research.plugin.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.PatternSpecificVertex
import org.jetbrains.research.plugin.jgrapht.createPatternGraph
import org.jgrapht.Graph
import org.jgrapht.graph.AsSubgraph
import java.nio.file.Path
import java.nio.file.Paths


object PatternsPreprocessor {
    private val finalPatternGraphByPatternDirPath =
        HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>()
    private val patternFragmentsGraphsByPatternDirPath =
        HashMap<Path, ArrayList<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()

    fun run() {
        loadOriginalPatterns()
        processVariableLabelsGroups()
        saveVariableLabelsGroups()
        deleteUnnecessaryFiles()
    }

    private fun loadOriginalPatterns() {
        Config.PATTERNS_STORAGE_PATH.toFile().walk().forEach {
            if (it.isFile && it.extension == "dot" && it.name.startsWith("fragment")) {
                val currentGraph = PatternSpecificGraphsLoader.loadDAGFromDotInputStream(it.inputStream())
                val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                    currentGraph,
                    currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
                )
                patternFragmentsGraphsByPatternDirPath.getOrPut(Paths.get(it.parent)) { ArrayList() }
                    .add(subgraphBefore)
            }
        }
    }

    private fun collectVariableLabelsGroups(fragments: List<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>)
            : ArrayList<HashSet<String>> {
        val variableLabelsGroups = ArrayList<HashSet<String>>()
        for (graph in fragments) {
            var varsCnt = 0
            for (vertex in graph.vertexSet()) {
                if (vertex.label?.startsWith("var") == true) {
                    if (varsCnt >= variableLabelsGroups.size) {
                        variableLabelsGroups.add(hashSetOf())
                    } else {
                        variableLabelsGroups[varsCnt].add(vertex.originalLabel!!)
                    }
                    varsCnt++
                }
            }
        }
        return variableLabelsGroups
    }

    private fun processVariableLabelsGroups() {
        for ((pathToPatternDir, fragmentsGraphs) in patternFragmentsGraphsByPatternDirPath) {
            finalPatternGraphByPatternDirPath[pathToPatternDir] =
                createPatternGraph(
                    baseGraph = fragmentsGraphs.first(),
                    variableLabelsGroups = collectVariableLabelsGroups(fragmentsGraphs)
                )
        }
    }

    private fun saveVariableLabelsGroups() {
        for ((pathToPatternDir, graph) in finalPatternGraphByPatternDirPath) {
            val varLabels = ArrayList<HashSet<String>>()
            for (vertex in graph.vertexSet()) {
                if (vertex.label?.startsWith("var") == true) {
                    varLabels.add(vertex.possibleVarLabels)
                }
            }
            val varLabelsJsonString = Gson().toJson(varLabels)
            val varLabelsFile = pathToPatternDir.resolve("possible_variable_labels.json").toFile()
            varLabelsFile.writeText(varLabelsJsonString)
        }
    }

    private fun deleteUnnecessaryFiles() {
        Config.PATTERNS_STORAGE_PATH.toFile().listFiles()?.forEach { patternDir ->
            val dotFile = patternDir
                .listFiles { file -> file.extension == "dot" && file.name.startsWith("fragment") }
                ?.first()
            val varGroupsJsonFile = patternDir
                .listFiles { file -> file.name == "possible_variable_labels.json" }
                ?.first()
            patternDir.listFiles()
                ?.filter { file -> file != dotFile && file != varGroupsJsonFile }
                ?.forEach { file -> file.delete() }
        }
    }
}

fun main(args: Array<String>) {
    PatternsPreprocessor.run()
}