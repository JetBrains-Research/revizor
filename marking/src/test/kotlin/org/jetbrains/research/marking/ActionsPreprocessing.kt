package org.jetbrains.research.marking

import com.github.gumtreediff.actions.ActionGenerator
import com.github.gumtreediff.actions.model.*
import com.github.gumtreediff.matchers.Matchers
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyFunction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.research.plugin.PatternGraph
import org.jetbrains.research.plugin.buildPyFlowGraphForMethod
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.gumtree.PyPsiGumTreeGenerator
import org.jetbrains.research.plugin.gumtree.wrappers.DeleteActionWrapper
import org.jetbrains.research.plugin.gumtree.wrappers.InsertActionWrapper
import org.jetbrains.research.plugin.gumtree.wrappers.MoveActionWrapper
import org.jetbrains.research.plugin.gumtree.wrappers.UpdateActionWrapper
import org.jetbrains.research.plugin.jgrapht.createPatternSpecificGraph
import org.jetbrains.research.plugin.jgrapht.getWeakSubgraphIsomorphismInspector
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jgrapht.graph.AsSubgraph
import java.io.File


class ActionsPreprocessing : BasePlatformTestCase() {

    companion object {
        const val PATTERNS_SRC = "/home/oleg/prog/data/plugin/jar_patterns/patterns"
        const val PATTERNS_DEST = "/home/oleg/prog/data/plugin/jar_patterns/preprocessed"
    }

    private val logger = Logger.getInstance(this::class.java)
    private val fragmentToPatternMappingByPattern =
        HashMap<String, HashMap<PatternSpecificVertex, PatternSpecificVertex>>()
    private val psiToPatternMappingByPattern = HashMap<String, HashMap<PyElement, PatternSpecificVertex>>()
    private val patternGraphByPattern = HashMap<String, PatternGraph>()
    private val actionsCache = HashMap<String, List<Action>>()
    private val psiCache = HashMap<File, PyElement>()

    private fun loadPsi(file: File): PyFunction? {
        return if (psiCache.containsKey(file)) {
            psiCache[file] as PyFunction
        } else {
            val src: String = file.readText()
            val psi = PsiFileFactory.getInstance(myFixture.project)
                .createFileFromText(PythonLanguage.getInstance(), src)
                .children.first() as PyFunction
            psiCache[file] = psi
            psi
        }
    }

    private fun loadEditActions(patternDir: File): List<Action> {
        return if (actionsCache.containsKey(patternDir.name)) {
            actionsCache[patternDir.name]!!
        } else {
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
            actionsCache[patternDir.name] = actions
            actions
        }
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
        patternGraphByPattern[patternDir.name] = createPatternSpecificGraph(subgraphBefore, labelsGroups)
    }

    private fun loadFragmentMappings(patternDir: File) {
        val fragmentPsi = loadPsi(patternDir.toPath().resolve("before.py").toFile())!!
        val patternGraph = patternGraphByPattern[patternDir.name]!!
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
            fragmentToPatternMappingByPattern[patternDir.name] = fragmentToPatternMapping
            psiToPatternMappingByPattern[patternDir.name] = psiToPatternMapping
        } else {
            logger.error("Pattern's graph does not match to pattern's code fragment (before.py)")
        }
    }

    private fun collectAdditionalNodes(patternDir: File): Set<PyElement> {
        // Collect elements which are involved in edit actions but are not contained in pattern graph
        val psiToPatternVertex = psiToPatternMappingByPattern[patternDir.name]!!
        val actions = loadEditActions(patternDir)
        val insertedElements = hashSetOf<PyElement>()
        val hangerElements = hashSetOf<PyElement>()
        for (action in actions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            if (action is Update || action is Delete || action is Move) {
                if (!psiToPatternVertex.containsKey(element))
                    hangerElements.add(element)
            }
            if (action is Insert) {
                val newElement = (action.node as PyPsiGumTree).rootElement ?: continue
                insertedElements.add(newElement)
            }
            if (action is Insert || action is Move) {
                val parent = (action as? Insert)?.parent ?: (action as? Move)?.parent
                val parentElement = (parent as PyPsiGumTree).rootElement!!
                if (insertedElements.contains(parentElement))
                    continue
                hangerElements.add(parentElement)
                if (!psiToPatternVertex.containsKey(parentElement))
                    hangerElements.add(parentElement)
            }
        }
        return hangerElements
    }

    private fun serializeActions(patternDir: File): String {
        val psiToPatternVertex = psiToPatternMappingByPattern[patternDir.name]!!
        val actions = loadEditActions(patternDir)
        val serializedActions = arrayListOf<String>()
        for (action in actions) {
            val element = (action.node as PyPsiGumTree).rootElement!!
            when (action) {
                is Update -> {
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    serializedActions.add(Json.encodeToString(UpdateActionWrapper(action)))
                }
                is Delete -> {
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    serializedActions.add(Json.encodeToString(DeleteActionWrapper(action)))
                }
                is Insert -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                    serializedActions.add(Json.encodeToString(InsertActionWrapper(action)))
                }
                is Move -> {
                    val parentElement = (action.parent as PyPsiGumTree).rootElement!!
                    (action.parent as PyPsiGumTree).rootVertex = psiToPatternVertex[parentElement]
                    (action.node as PyPsiGumTree).rootVertex = psiToPatternVertex[element]
                    serializedActions.add(Json.encodeToString(MoveActionWrapper(action)))
                }
            }
        }
        return serializedActions.joinToString(",", "[", "]")
    }

    fun test() {
        File(PATTERNS_SRC).listFiles()?.forEach { patternDir ->
            loadGraph(patternDir)
            loadFragmentMappings(patternDir)
            val hangerElements = collectAdditionalNodes(patternDir)

            val serializedActions = serializeActions(patternDir)
            val dest = File(PATTERNS_DEST).resolve(patternDir.name)
            patternDir.copyRecursively(dest, overwrite = true)
            dest.resolve("actions.json").writeText(serializedActions)
        }
    }
}