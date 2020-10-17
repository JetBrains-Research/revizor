package org.jetbrains.research.plugin.ide

import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Insert
import com.github.gumtreediff.actions.model.Move
import com.github.gumtreediff.actions.model.Update
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
import org.jetbrains.research.plugin.modifying.PyElementTransformer
import org.jetbrains.research.plugin.modifying.PyPsiGumTree
import org.jgrapht.GraphMapping

class PatternBasedAutoFix(
        private val problematicVertex: PatternSpecificVertex,
        private val mappingsHolder: BugFinderInspection.PyMethodsAnalyzer.DetectedVertexMappingsHolder
) : LocalQuickFix {

    private val logger = Logger.getInstance(this::class.java)

    override fun getFamilyName(): String = "BugFinder: autofix using pattern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val suggestionsPopup = JBPopupFactory.getInstance()
                    .createListPopup(FixSuggestionsListPopupStep())
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                suggestionsPopup.showInBestPositionFor(editor)
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }

    inner class FixSuggestionsListPopupStep : BaseListPopupStep<String>(
            "Patterns", mappingsHolder.patternsIdsByVertex[problematicVertex]?.toList() ?: listOf()
    ) {
        private var selectedPatternId: String = ""
        private val newElementByTree = HashMap<PyPsiGumTree, PyElement>()
        private lateinit var vertexMapping: GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>

        override fun getTextFor(patternId: String) = PatternsStorage.getPatternDescriptionById(patternId)

        override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
            selectedPatternId = selectedValue
            return super.onChosen(selectedValue, finalChoice)
        }

        override fun getFinalRunnable(): Runnable? {
            return Runnable { applyEditFromPattern(selectedPatternId) }
        }

        private fun applyEditFromPattern(patternId: String) {
            val actions = PatternsStorage.getPatternEditActionsById(patternId)
            val transformer = PyElementTransformer(PatternsStorage.project)
            newElementByTree.clear()
            vertexMapping = mappingsHolder.vertexMappingsByTargetVertex[problematicVertex]!![patternId]!!
            val namesMapping = mappingsHolder.varNamesMappingByVertexMapping[vertexMapping]!!
            for (action in actions) {
                try {
                    when (action) {
                        is Update -> {
                            val targetElement = extractPsiElementFromPyPsiGumTree(
                                    action.node as PyPsiGumTree,
                                    patternId
                            )
                            val updatedElement = transformer.applyUpdate(targetElement, action)
                            newElementByTree[action.node as PyPsiGumTree] = updatedElement
                        }
                        is Delete -> {
                            val targetElement = extractPsiElementFromPyPsiGumTree(
                                    action.node as PyPsiGumTree,
                                    patternId
                            )
                            transformer.applyDelete(targetElement, action)
                        }
                        is Insert -> {
                            val targetParentElement = extractPsiElementFromPyPsiGumTree(
                                    action.parent as PyPsiGumTree,
                                    patternId
                            )
                            val patternVarName = action.node.label.substringAfterLast(":", "").trim()
                            val targetVarName = namesMapping[patternVarName] ?: patternVarName
                            action.node.label = action.node.label.replaceAfterLast(": ", targetVarName)
                            val newElement = transformer.applyInsert(targetParentElement, action)
                            newElementByTree[action.node as PyPsiGumTree] = newElement
                        }
                        is Move -> {
                            val targetParentElement = extractPsiElementFromPyPsiGumTree(
                                    action.parent as PyPsiGumTree,
                                    patternId
                            )
                            val targetElement = extractPsiElementFromPyPsiGumTree(action.node as PyPsiGumTree, patternId)
                            val movedElement = transformer.applyMove(targetElement, targetParentElement, action)
                            newElementByTree[action.node as PyPsiGumTree] = movedElement
                        }
                    }
                } catch (ex: Throwable) {
                    logger.warn("Can not apply the action $action")
                    logger.warn(ex)
                    continue
                }
            }
        }

        private fun extractPsiElementFromPyPsiGumTree(tree: PyPsiGumTree, patternId: String) =
                if (newElementByTree.containsKey(tree))
                    newElementByTree[tree]!!
                else
                    vertexMapping.getVertexCorrespondence(tree.rootVertex, false).origin?.psi!!
    }

}