package org.jetbrains.research.marking

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.PatternDirectedAcyclicGraph
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.getSuperWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.loadPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.AsSubgraph
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

const val SRC = "/home/oleg/prog/data/plugin/output_final_best/"
const val DEST = "/home/oleg/prog/data/plugin/output_final_best_1/"

private val descriptionByPath = HashMap<Path, String>()
private val labelsGroupsJsonByPath = HashMap<Path, String>()


private fun loadFragments(inputPatternsStorage: String): HashMap<Path, ArrayList<PatternGraph>> {
    val fragmentsByDir = HashMap<Path, ArrayList<PatternGraph>>()
    File(inputPatternsStorage).walk().forEach { file ->
        if (file.isFile && file.name.startsWith("fragment") && file.extension == "dot") {
            val currentGraph = loadPatternSpecificGraph(file.inputStream())
            val subgraphBefore = AsSubgraph(
                currentGraph,
                currentGraph.vertexSet()
                    .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                    .toSet()
            )
            fragmentsByDir.getOrPut(Paths.get(file.parent)) { ArrayList() }.add(subgraphBefore)
        }
    }
    return fragmentsByDir
}

private fun mergeFragments(fragmentsMap: HashMap<Path, ArrayList<PatternGraph>>): HashMap<Path, PatternDirectedAcyclicGraph> {
    val patternGraphByPath = HashMap<Path, PatternDirectedAcyclicGraph>()
    for ((path, fragments) in fragmentsMap) {
        val labelsGroupsByVertexId = HashMap<Int, PatternSpecificVertex.LabelsGroup>()
        val repr = fragments.first()
        for (graph in fragments.drop(1)) {
            val inspector = getSuperWeakSubgraphIsomorphismInspector(repr, graph)
            if (!inspector.isomorphismExists()) {
                throw IllegalStateException("Fragments are not isomorphic in the pattern $path")
            } else {
                val mapping = inspector.mappings.asSequence().first()
                for (currentVertex in graph.vertexSet()) {
                    if (currentVertex.label?.startsWith("var") == true) {
                        val reprVertex = mapping.getVertexCorrespondence(currentVertex, false)
                        labelsGroupsByVertexId.getOrPut(reprVertex.id) { PatternSpecificVertex.LabelsGroup.getEmpty() }
                            .labels.add(currentVertex.originalLabel!!)
                    }
                }
            }
        }
        patternGraphByPath[path] = createPatternSpecificGraph(fragments.first(), labelsGroupsByVertexId)
    }
    return patternGraphByPath
}

private fun markPatterns(
    patternDirectedAcyclicGraphByPath: HashMap<Path, PatternDirectedAcyclicGraph>,
    addDescription: Boolean = false
) {
    println("Start marking")
    for ((path, graph) in patternDirectedAcyclicGraphByPath) {
        println("-".repeat(70))
        println("Path to current pattern: $path")

        // Add description, which will be shown in the popup (optional)
        if (addDescription) {
            val dotFile = path.toFile()
                .listFiles { file -> file.extension == "dot" && file.name.startsWith("fragment") }
                ?.first()
            println("Fragment sample ${dotFile?.name}")
            println(dotFile?.readText())
            println("Your description:")
            val description = readLine() ?: "No description provided"
            println("Final description: $description")
            descriptionByPath[path] = description
        }

        // Choose matching mode
        println("Variable original labels groups:")
        val labelsGroupsByVertexId = HashMap<Int, PatternSpecificVertex.LabelsGroup>()
        for (vertex in graph.vertexSet()) {
            if (vertex.label?.startsWith("var") == true) {
                println(vertex.dataNodeInfo?.labels)
                var exit = false
                while (!exit) {
                    println("Choose, what will be considered as main factor when matching nodes (labels/lcs/nothing):")
                    val ans = readLine()
                    println("Your answer: $ans")
                    when (ans) {
                        "labels" -> labelsGroupsByVertexId[vertex.id] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.VALUABLE_ORIGINAL_LABEL,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            ).also { exit = true }
                        "lcs" -> labelsGroupsByVertexId[vertex.id] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.LONGEST_COMMON_SUFFIX,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = getLongestCommonSuffix(vertex.dataNodeInfo?.labels)
                            ).also { exit = true }
                        "nothing" -> labelsGroupsByVertexId[vertex.id] =
                            PatternSpecificVertex.LabelsGroup(
                                whatMatters = PatternSpecificVertex.MatchingMode.NOTHING,
                                labels = vertex.dataNodeInfo!!.labels,
                                longestCommonSuffix = ""
                            ).also { exit = true }
                    }
                }
            }
        }
        labelsGroupsJsonByPath[path] = Json.encodeToString(labelsGroupsByVertexId)
    }
    println("Finish marking")
}

private fun getLongestCommonSuffix(strings: Collection<String?>?): String {
    if (strings == null || strings.isEmpty())
        return ""
    var lcs = strings.first()
    for (string in strings) {
        lcs = lcs?.commonSuffixWith(string ?: "")
    }
    return lcs ?: ""
}

private fun copyUsefulFiles(from: String, dest: String) {
    File(from).listFiles()?.forEach { patternDir ->
        val labelsGroupsFile = Paths.get(dest, patternDir.name, "labels_groups.json").toFile().also {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.writeText(labelsGroupsJsonByPath[patternDir.toPath()]!!)
        }
        val descFile = Paths.get(dest, patternDir.name, "description.txt").toFile().also {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.writeText(descriptionByPath[patternDir.toPath()] ?: "No description provided")
        }
        patternDir.listFiles { file -> file.name.startsWith("fragment") && file.extension == "dot" }
            ?.first()?.copyTo(Paths.get(dest, patternDir.name, "graph.dot").toFile(), overwrite = false)
    }
}

fun main(args: Array<String>) {
    val fragmentsMap = loadFragments(SRC)
    val patternsGraphsMap = mergeFragments(fragmentsMap)
    markPatterns(patternsGraphsMap, false)
    copyUsefulFiles(from = SRC, dest = DEST)
}