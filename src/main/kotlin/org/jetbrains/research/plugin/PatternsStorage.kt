package org.jetbrains.research.plugin

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.matchers.Matchers
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import org.jetbrains.research.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.plugin.common.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.GraphMapping
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.jar.JarFile

typealias PatternGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>

/**
 * A singleton class for storing graph patterns.
 *
 * This class provides functionality for retrieving patterns from JAR file,
 * extracting descriptions and common variables' labels for each pattern. Also,
 * it has `getIsomorphicPatterns` method which is used for patterns localization.
 */
object PatternsStorage {
    private val patternDescById = HashMap<String, String>()
    private val patternById = HashMap<String, PatternGraph>()
    private val patternPsiMappingById = HashMap<String, Map<PatternSpecificVertex, PyElement?>>()
    private val patternEditActionsById = HashMap<String, List<Action>>()
    private val patternPsiBeforeById = HashMap<String, PyFunction?>()
    private val patternPsiAfterById = HashMap<String, PyFunction?>()
    private val logger = Logger.getInstance(this::class.java)
    lateinit var project: Project

    fun init(project: Project) {
        this.project = project
        var jar: JarFile? = null
        try {
            val resourceFile = File(this::class.java.getResource("").path)
            val jarFilePath = Paths.get(URL(resourceFile.path).toURI()).toString().substringBeforeLast("!")
            jar = JarFile(jarFilePath)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val je = entries.nextElement()
                val jarEntryPathParts = je.name.split("/")
                val patternId = jarEntryPathParts.getOrNull(1)
                if (jarEntryPathParts.size == 3
                    && jarEntryPathParts.first() == "patterns"
                    && patternId?.toIntOrNull() != null
                    && jarEntryPathParts.last().endsWith(".dot")
                ) {
                    val dotSrcStream = this::class.java.getResourceAsStream("/${je.name}")
                    val fullGraph = createPatternSpecificGraph(dotSrcStream)
                    val subgraphBefore = AsSubgraph<PatternSpecificVertex, PatternSpecificMultipleEdge>(
                        fullGraph,
                        fullGraph.vertexSet()
                            .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                            .toSet()
                    )
                    val variableLabelsGroups = loadVariableLabelsGroups(patternId) ?: arrayListOf()
                    val targetGraph: PatternGraph = createPatternSpecificGraph(subgraphBefore, variableLabelsGroups)
                    patternById[patternId] = targetGraph
                    patternDescById[patternId] = loadDescription(patternId) ?: "No description provided"
                    patternPsiBeforeById[patternId] = loadPsiMethodFromPattern(patternId, "before.py")
                    patternPsiAfterById[patternId] = loadPsiMethodFromPattern(patternId, "after.py")
                    patternPsiMappingById[patternId] = loadPsiMappingFromPattern(patternId, targetGraph)
                    patternEditActionsById[patternId] = loadEditActionsFromPattern(patternId)
                }
            }
        } catch (ex: Exception) {
            logger.warn("Failed to load patterns resources from JAR file")
        } finally {
            jar?.close()
        }
    }

    fun getPatternById(patternId: String): PatternGraph? =
        patternById[patternId]

    fun getPatternDescriptionById(patternId: String): String =
        patternDescById[patternId] ?: "Unnamed pattern: $patternId"

    fun getPatternPsiElementByIdAndVertex(patternId: String, vertex: PatternSpecificVertex): PyElement? =
        patternPsiMappingById[patternId]?.get(vertex)

    fun getPatternEditActionsById(patternId: String): List<Action> =
        patternEditActionsById[patternId] ?: arrayListOf()

    fun getPatternPsiBeforeById(patternId: String): PyFunction? =
        patternPsiBeforeById[patternId] ?: loadPsiMethodFromPattern(patternId, "before.py")

    fun getPatternPsiAfterById(patternId: String): PyFunction? =
        patternPsiAfterById[patternId] ?: loadPsiMethodFromPattern(patternId, "after.py")

    fun getIsomorphicPatterns(targetGraph: PatternGraph)
            : HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>> {
        val suitablePatterns =
            HashMap<String, ArrayList<GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>>>()
        for ((patternId, patternGraph) in patternById) {
            val inspector = getWeakSubgraphIsomorphismInspector(targetGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                for (mapping in inspector.mappings) {
                    suitablePatterns.getOrPut(patternId) { arrayListOf() }.add(mapping)
                }
            }
        }
        return suitablePatterns
    }

    private fun loadDescription(patternId: String): String? {
        return try {
            this::class.java.getResource("/patterns/$patternId/description.txt").readText()
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadVariableLabelsGroups(patternId: String): ArrayList<PatternSpecificVertex.LabelsGroup>? {
        return try {
            val filePath = "/patterns/$patternId/possible_variable_labels.json"
            val fileContent = this::class.java.getResource(filePath).readText()
            val type = object : TypeToken<ArrayList<PatternSpecificVertex.LabelsGroup>>() {}.type
            val json = Gson().fromJson<ArrayList<PatternSpecificVertex.LabelsGroup>>(fileContent, type)
            val labelsGroups = ArrayList<PatternSpecificVertex.LabelsGroup>()
            json.forEach {
                labelsGroups.add(
                    PatternSpecificVertex.LabelsGroup(
                        whatMatters = it.whatMatters,
                        labels = it.labels.toHashSet(),
                        longestCommonSuffix = it.longestCommonSuffix
                    )
                )
            }
            labelsGroups
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadPsiMappingFromPattern(
        patternId: String,
        patternGraph: PatternGraph
    ): Map<PatternSpecificVertex, PyElement> {
        val psiNodeByPatternSpecificVertex: HashMap<PatternSpecificVertex, PyElement> = hashMapOf()
        try {
            val samplePsi = patternPsiBeforeById[patternId]!!
            val sampleGraph = buildPyFlowGraphForMethod(samplePsi, builder = "kotlin")
            val inspector = getWeakSubgraphIsomorphismInspector(sampleGraph, patternGraph)
            if (inspector.isomorphismExists()) {
                val mapping = inspector.mappings.asSequence().first()
                for (patternVertex in patternGraph.vertexSet()) {
                    val sampleVertex = mapping.getVertexCorrespondence(patternVertex, false)
                    if (sampleVertex.origin?.psi != null) {
                        psiNodeByPatternSpecificVertex[patternVertex] = sampleVertex.origin?.psi!!
                    }
                }
            } else {
                logger.error("Pattern's graph doesn't match to pattern's code sample (before.py)")
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return psiNodeByPatternSpecificVertex
    }

    private fun loadEditActionsFromPattern(patternId: String): List<Action> {
        var actions: List<Action> = arrayListOf()
        try {
            val pyFunctionBefore = patternPsiBeforeById[patternId]!!
            val pyFunctionAfter = patternPsiAfterById[patternId]!!
            val srcGumtree = PyPsiGumTreeGenerator().generate(pyFunctionBefore).root
            val dstGumtree = PyPsiGumTreeGenerator().generate(pyFunctionAfter).root
            val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree)
                .also { it.match() }
            val mappings = matcher.mappings
            val generator = ActionGenerator(srcGumtree, dstGumtree, mappings)
            actions = generator.generate()
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return actions
    }

    private fun loadPsiMethodFromPattern(patternId: String, fileName: String): PyFunction? {
        return try {
            val pathToSampleBefore = "/patterns/$patternId/$fileName"
            val sampleSrc: String = this::class.java.getResource(pathToSampleBefore).readText()
            PsiFileFactory.getInstance(project)
                .createFileFromText(PythonLanguage.getInstance(), sampleSrc)
                .children.first() as PyFunction
        } catch (ex: Exception) {
            logger.error(ex)
            null
        }
    }
}