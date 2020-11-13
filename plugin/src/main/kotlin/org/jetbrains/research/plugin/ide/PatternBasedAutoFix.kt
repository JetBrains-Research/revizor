package org.jetbrains.research.plugin.ide

import com.github.gumtreediff.actions.model.*
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.util.collectDescendantsOfType
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.PatternsStorage
import org.jetbrains.research.plugin.gumtree.PyElementTransformer
import org.jetbrains.research.plugin.gumtree.PyPsiGumTree
import org.jetbrains.research.plugin.gumtree.getAllTreesFromActions
import org.jetbrains.research.plugin.jgrapht.edges.PatternSpecificMultipleEdge
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex
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
        private lateinit var vertexMapping: GraphMapping<PatternSpecificVertex, PatternSpecificMultipleEdge>
        private lateinit var namesMapping: Map<String, String>
        private val elementByTree = hashMapOf<PyPsiGumTree, PyElement>()
        private val revisions = hashMapOf<PyElement, PyElement>()

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
            vertexMapping = mappingsHolder.vertexMappingsByTargetVertex[problematicVertex]!![patternId]!!
            namesMapping = mappingsHolder.varNamesMappingByVertexMapping[vertexMapping]!!
            elementByTree.clear()
            for (tree in getAllTreesFromActions(actions)) {
                val pyPsiGumTree = tree as PyPsiGumTree
                val element = vertexMapping.getVertexCorrespondence(pyPsiGumTree.rootVertex, false)
                        ?.origin?.psi ?: continue
                elementByTree[pyPsiGumTree] = element
            }
            revisions.clear()
            WriteCommandAction.runWriteCommandAction(PatternsStorage.project) {
                for (action in actions) {
                    try {
                        when (action) {
                            is Update -> {
                                val update = replaceVarNames(action) as Update
                                val targetElement =
                                        findRevision(elementByTree[update.node as PyPsiGumTree]!!, revisions)
                                val newElement = transformer.applyUpdate(targetElement, update)
                                updateRevisions(targetElement, newElement, revisions)
                            }
                            is Delete -> {
                                val targetElement =
                                        findRevision(elementByTree[action.node as PyPsiGumTree]!!, revisions)
                                transformer.applyDelete(targetElement, action)
                            }
                            is Insert -> {
                                val targetParentElement =
                                        findRevision(elementByTree[action.parent as PyPsiGumTree]!!, revisions)
                                val insert = replaceVarNames(action) as Insert
                                val newElement = transformer.applyInsert(targetParentElement, insert)
                                elementByTree[insert.node as PyPsiGumTree] = newElement
                            }
                            is Move -> {
                                val targetParentElement =
                                        findRevision(elementByTree[action.parent as PyPsiGumTree]!!, revisions)
                                val targetElement =
                                        findRevision(elementByTree[action.node as PyPsiGumTree]!!, revisions)
                                val movedElement = transformer.applyMove(targetElement, targetParentElement, action)
                                updateRevisions(targetElement, movedElement, revisions)
                            }
                        }
                    } catch (ex: Throwable) {
                        logger.warn("Can not apply the action $action")
                        logger.warn(ex)
                        continue
                    }
                }
            }
        }

        private fun updateRevisions(oldElement: PyElement,
                                    newElement: PyElement,
                                    revisions: MutableMap<PyElement, PyElement>) {
            val oldDescendants = oldElement.collectDescendantsOfType<PyElement>()
            val newDescendants = newElement.collectDescendantsOfType<PyElement>()
            for ((old, new) in oldDescendants.zip(newDescendants)) {
                revisions[old] = new
            }
            revisions[oldElement] = newElement
        }

        private fun findRevision(element: PyElement, revisions: MutableMap<PyElement, PyElement>): PyElement {
            var currentRevision = element
            while (revisions.containsKey(currentRevision)) {
                if (currentRevision == revisions[currentRevision])
                    break
                currentRevision = revisions[currentRevision]!!
            }
            return currentRevision
        }

        private fun replaceVarNames(action: Action): Action {
            val oldVarName = action.node.label.substringAfterLast(":", "").trim()
            val newVarName = getNewNameByMapping(oldVarName)
            action.node.label = action.node.label.replaceAfterLast(": ", newVarName)
            if (action is Update) {
                val updatedOldVarName = action.value.substringAfterLast(":", "").trim()
                val updatedNewVarName = getNewNameByMapping(updatedOldVarName)
                return Update(action.node, action.value.replaceAfterLast(": ", updatedNewVarName))
            }
            return action
        }

        private fun getNewNameByMapping(oldName: String): String {
            return if (namesMapping.containsKey(oldName)) {
                namesMapping.getValue(oldName)
            } else {
                var newName = ""
                for (attrOldName in oldName.split(".")) {
                    newName += namesMapping[attrOldName] ?: attrOldName
                    newName += "."
                }
                newName.dropLast(1)
            }
        }
    }

}