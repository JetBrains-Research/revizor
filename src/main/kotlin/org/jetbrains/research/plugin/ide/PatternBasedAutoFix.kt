package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.research.plugin.jgrapht.vertices.PatternSpecificVertex

class PatternBasedAutoFix(
    private val problematicVertex: PatternSpecificVertex,
    private val holder: BugFinderInspection.PyMethodsAnalyzer.FoundProblemsHolder
) : LocalQuickFix {
    private val logger = Logger.getInstance(this::class.java)

    override fun getFamilyName(): String = "BugFinder: autofix using pattern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
                FixSuggestionsListPopupStep(problematicVertex, holder)
            )
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                suggestionsPopup.showInBestPositionFor(editor)
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }
}