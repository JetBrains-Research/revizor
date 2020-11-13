package org.jetbrains.research.marking

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DirectedAcyclicGraph
import java.io.File

typealias PatternGraph = DirectedAcyclicGraph<PatternSpecificVertex, PatternSpecificMultipleEdge>

class ActionsPreprocessing : BasePlatformTestCase() {

    companion object {
        const val PATH_TO_PATTERNS = "/home/oleg/prog/data/plugin/jar_patterns/patterns"
        const val DEST = "/home/oleg/prog/data/plugin/jar_patterns/preprocessed"
    }

    private val logger = Logger.getInstance(this::class.java)
    private val fragmentToPatternMappingByHash = HashMap<Int, HashMap<PatternSpecificVertex, PatternSpecificVertex>>()
    private val psiToPatternMappingByHash = HashMap<Int, HashMap<PyElement, PatternSpecificVertex>>()
    private val patternGraphByHash = HashMap<Int, PatternGraph>()

    private fun loadPsi(file: File): PyFunction? {
        val src: String = file.readText()
        return PsiFileFactory.getInstance(myFixture.project)
            .createFileFromText(PythonLanguage.getInstance(), src)
            .children.first() as PyFunction
    }

    private fun loadEditActionsFromPattern(patternDir: File): List<Action> {
        var actions: List<Action> = arrayListOf()
        try {
            val pyFunctionBefore = loadPsi(patternDir.toPath().resolve("before.py").toFile())!!
            val pyFunctionAfter = loadPsi(patternDir.toPath().resolve("after.py").toFile())!!
            val srcGumtree = PyPsiGumTreeGenerator().generate(pyFunctionBefore).root
            val dstGumtree = PyPsiGumTreeGenerator().generate(pyFunctionAfter).root
            val matcher = Matchers.getInstance().getMatcher(srcGumtree, dstGumtree).also { it.match() }
            val generator = ActionGenerator(srcGumtree, dstGumtree, matcher.mappings)
            actions = generator.generate()
        } catch (ex: Exception) {
            logger.error(ex)
        }
        return actions
    }

    private fun loadVariableLabelsGroups(patternDir: File): List<PatternSpecificVertex.LabelsGroup> {
        val src = patternDir.toPath().resolve("possible_variable_labels.json").toFile().readText()
        return Json.decodeFromString(src)
    }

    private fun loadGraph(patternDir: File) {
        val dotFiles = patternDir.listFiles { _, name -> name.endsWith(".dot") }!!
        val inputDotStream = dotFiles[0].inputStream()
        val changeGraph = createPatternSpecificGraph(inputDotStream)
        val subgraphBefore = AsSubgraph(
            changeGraph,
            changeGraph.vertexSet()
                .filter { it.fromPart == PatternSpecificVertex.ChangeGraphPartIndicator.BEFORE }
                .toSet()
        )
        val labelsGroups = loadVariableLabelsGroups(patternDir)
        patternGraphByHash[patternDir.hashCode()] = createPatternSpecificGraph(subgraphBefore, labelsGroups)
    }

    private fun loadFragmentMappings(patternDir: File) {
        val fragmentPsi = loadPsi(patternDir.toPath().resolve("before.py").toFile())!!
        val patternGraph = patternGraphByHash[patternDir.hashCode()]!!
        val fragmentGraph = buildPyFlowGraphForMethod(fragmentPsi, builder = "kotlin")
        val fragmentToPatternMapping = HashMap<PatternSpecificVertex, PatternSpecificVertex>()
        val psiToPatternMapping = HashMap<PyElement, PatternSpecificVertex>()
        val inspector = getWeakSubgraphIsomorphismInspector(fragmentGraph, patternGraph)
        if (inspector.isomorphismExists()) {
            val mapping = inspector.mappings.asSequence().first()
            for (patternVertex in patternGraph.vertexSet()) {
                val fragmentVertex = mapping.getVertexCorrespondence(patternVertex, false)
                fragmentToPatternMapping[fragmentVertex] = patternVertex
                psiToPatternMapping[fragmentVertex.origin!!.psi!!] = patternVertex
            }
            fragmentToPatternMappingByHash[patternDir.hashCode()] = fragmentToPatternMapping
            psiToPatternMappingByHash[patternDir.hashCode()] = psiToPatternMapping
        } else {
            logger.error("Pattern's graph does not match to pattern's code fragment (before.py)")
        }
    }

    fun test() {
        File(PATH_TO_PATTERNS).listFiles()?.forEach { patternDir ->
            val actions = loadEditActionsFromPattern(patternDir)
            loadGraph(patternDir)
            loadFragmentMappings(patternDir)
        }
    }
}