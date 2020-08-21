package org.jetbrains.research.marking

import com.google.gson.Gson
import org.jetbrains.research.plugin.common.getLongestCommonSuffix
import org.jetbrains.research.plugin.jgrapht.PatternSpecificGraphFactory
import org.jetbrains.research.plugin.jgrapht.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.PatternSpecificVertex
import org.jgrapht.Graph
import org.jgrapht.graph.AsSubgraph
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.cli.*

typealias FragmentsByPathMap = HashMap<Path, ArrayList<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>>
typealias PatternGraphByPathMap = HashMap<Path, Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>


private fun loadFragments(inputPatternsStorage: String): FragmentsByPathMap {
    val fragmentsByDirectory = FragmentsByPathMap()
    File(inputPatternsStorage).walk().forEach {
        if (it.isFile && it.extension == "dot" && it.name.startsWith("fragment")) {
            val currentGraph = PatternSpecificGraphFactory.createGraph(it.inputStream())
            val subgraphBefore = AsSubgraph(
                currentGraph,
                currentGraph.vertexSet().filter { vertex -> vertex.color == "red2" }.toSet()
            )
            fragmentsByDirectory.getOrPut(Paths.get(it.parent)) { ArrayList() }.add(subgraphBefore)
        }
    }
    return fragmentsByDirectory
}

private fun getVarsGroups(fragments: List<Graph<PatternSpecificVertex, PatternSpecificMultipleEdge>>)
        : ArrayList<PatternSpecificVertex.LabelsGroup> {
    val labelsGroups = ArrayList<PatternSpecificVertex.LabelsGroup>()
    for (graph in fragments) {
        var varsCnt = 0
        for (vertex in graph.vertexSet()) {
            if (vertex.label?.startsWith("var") == true) {
                if (varsCnt >= labelsGroups.size) {
                    labelsGroups.add(
                        PatternSpecificVertex.LabelsGroup(
                            whatMatters = null,
                            labels = hashSetOf(vertex.originalLabel!!),
                            longestCommonSuffix = ""
                        )
                    )
                } else {
                    labelsGroups[varsCnt].labels.add(vertex.originalLabel!!)
                }
                varsCnt++
            }
        }
    }
    return labelsGroups
}

private fun collectVariableLabelsGroups(fragmentsMap: FragmentsByPathMap): PatternGraphByPathMap {
    val graphs = PatternGraphByPathMap()
    for ((path, fragmentsGraphs) in fragmentsMap) {
        graphs[path] = PatternSpecificGraphFactory.createGraph(
            baseGraph = fragmentsGraphs.first(),
            variableLabelsGroups = getVarsGroups(fragmentsGraphs)
        )
    }
    return graphs
}

private fun markPatterns(patternsGraphs: PatternGraphByPathMap, addDescription: Boolean) {
    println("Start marking")
    for ((path, graph) in patternsGraphs) {
        println("-".repeat(70))
        println("Path to current pattern: $path")
        if (addDescription) {
            val dotFile = path.toFile()
                .listFiles { file -> file.extension == "dot" && file.name.startsWith("fragment") }
                ?.first()
            println("Fragment sample ${dotFile?.name}")
            println(dotFile?.readText())
            println("Your description:")
            val description = readLine() ?: "No description provided"
            println("Final description: $description")
            val descFile = path.resolve("description.txt").toFile()
            descFile.writeText(description)
        }
        println("Variable original labels groups:")
        val labelsGroups = ArrayList<PatternSpecificVertex.LabelsGroup>()
        for (vertex in graph.vertexSet()) {
            if (vertex.label?.startsWith("var") == true) {
                println(vertex.dataNodeInfo?.labels)
                var exit = false
                while (!exit) {
                    println("Choose, what will be considered as main factor when matching nodes (labels/lcs/nothing):")
                    val ans = readLine()
                    println("Your answer: $ans")
                    when (ans) {
                        "labels" -> labelsGroups.add(
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.LabelsGroup.Indicator.VALUABLE_ORIGINAL_LABEL,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            )
                        ).also { exit = true }
                        "lcs" -> labelsGroups.add(
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.LabelsGroup.Indicator.LONGEST_COMMON_SUFFIX,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = getLongestCommonSuffix(vertex.dataNodeInfo?.labels)
                            )
                        ).also { exit = true }
                        "nothing" -> labelsGroups.add(
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.LabelsGroup.Indicator.NOTHING,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            )
                        ).also { exit = true }
                    }
                }
            }
        }
        val varLabelsJsonString = Gson().toJson(labelsGroups)
        val varLabelsFile = path.resolve("possible_variable_labels.json").toFile()
        varLabelsFile.writeText(varLabelsJsonString)
    }
    println("Finish marking")
}

private fun copyUsefulFiles(from: String, dest: String) {
    File(dest).mkdirs()
    File(from).listFiles()?.forEach { patternDir ->
        val dotFile = patternDir
            .listFiles { file -> file.extension == "dot" && file.name.startsWith("fragment") }
            ?.first()
        val varGroupsJsonFile = patternDir
            .listFiles { file -> file.name == "possible_variable_labels.json" }
            ?.first()
        val descFile = patternDir
            .listFiles { file -> file.name == "description.txt" }
            ?.first()
        patternDir.listFiles()
            ?.filter { file -> file == dotFile || file == varGroupsJsonFile || file == descFile }
            ?.forEach { file ->
                file.copyTo(
                    Paths.get(dest, patternDir.name, file.name).toFile(),
                    overwrite = true
                )
            }
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser(programName = "marking")
    val input by parser.option(
        type = ArgType.String,
        shortName = "i",
        description = "Input directory with patterns mined by code-change-miner tool"
    ).required()
    val output by parser.option(
        type = ArgType.String,
        shortName = "o",
        description = "Output directory for processed patterns"
    ).required()
    val addDescription by parser.option(
        type = ArgType.Boolean,
        fullName = "add-description",
        shortName = "d",
        description = "Add description manually for each pattern"
    ).default(false)
    parser.parse(args)
    val fragmentsMap = loadFragments(input)
    val patternsGraphsMap = collectVariableLabelsGroups(fragmentsMap)
    markPatterns(patternsGraphsMap, addDescription)
    copyUsefulFiles(from = input, dest = output)
}