package org.jetbrains.research.marking

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.Action
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.modifying.PyPsiGumTreeGenerator
import org.jgrapht.graph.AsSubgraph
import java.io.File


class ActionsPreprocessing : BasePlatformTestCase() {

    companion object {
        const val PATH_TO_PATTERNS = "/home/oleg/prog/data/plugin/jar_patterns/patterns"
    }

    private val logger = Logger.getInstance(this::class.java)

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
        val labelsGroups = Json.decodeFromString<List<PatternSpecificVertex.LabelsGroup>>(src)
        return labelsGroups
    }


    fun test() {
        File(PATH_TO_PATTERNS).listFiles()?.forEach { patternDir ->
            val actions = loadEditActionsFromPattern(patternDir)
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
            val patternGraph = createPatternSpecificGraph(subgraphBefore, labelsGroups)
        }
    }
}