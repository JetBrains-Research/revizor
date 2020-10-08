package org.jetbrains.research.plugin.ide

import com.github.gumtreediff.actions.model.Delete
import com.github.gumtreediff.actions.model.Insert
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
import org.jetbrains.research.gumtree.PyElementTransformer
import org.jetbrains.research.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex

class PatternBasedAutoFix(
    private val problematicVertex: PatternSpecificVertex,
    private val holder: BugFinderInspection.PyMethodsAnalyzer.FoundProblemsHolder
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
        "Patterns", holder.patternsIdsByVertex[problematicVertex]?.toList() ?: listOf()
    ) {

        private var selectedPatternId: String = ""

        override fun getTextFor(patternId: String) = PatternsStorage.getPatternDescriptionById(patternId)

        override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
            selectedPatternId = selectedValue
            return super.onChosen(selectedValue, finalChoice)
        }

        override fun getFinalRunnable(): Runnable? {
            return Runnable { applyEditFromPattern(selectedPatternId) }
        }

        private fun extractPsiElementFromPyPsiGumTree(tree: PyPsiGumTree, supportElements: Set<PyElement>) =
            if (tree.rootVertex == null) {
                supportElements.find { it.toString() == tree.toString() }
                    ?: throw IllegalStateException("Mismatched node $tree}")
            } else {
                val mapping = holder.mappingByVertex[tree.rootVertex!!]!!
                val targetVertex = mapping.getVertexCorrespondence(tree.rootVertex, false)
                targetVertex.origin?.psi!!
            }

        private fun applyEditFromPattern(patternId: String) {
            val actions = PatternsStorage.getPatternEditActionsById(patternId)
            val transformer = PyElementTransformer(PatternsStorage.project)
            val addedElements = hashSetOf<PyElement>()
            for (action in actions) {
                try {
                    if (action is Update || action is Delete) {
                        val targetElement = extractPsiElementFromPyPsiGumTree(
                            tree = action.node as PyPsiGumTree,
                            supportElements = addedElements
                        )
                        when (action) {
                            is Update -> transformer.applyUpdate(targetElement, action)
                            is Delete -> transformer.applyDelete(targetElement, action)
                        }
                    } else if (action is Insert) {
                        val targetParentElement = extractPsiElementFromPyPsiGumTree(
                            tree = action.parent as PyPsiGumTree,
                            supportElements = addedElements
                        )
                        val newElement = transformer.applyInsert(targetParentElement, action)
                        addedElements.add(newElement)
                    }
                } catch (ex: Throwable) {
                    logger.warn("Can not apply the action $action")
                    logger.warn(ex)
                    continue
                }
            }
        }
    }

}