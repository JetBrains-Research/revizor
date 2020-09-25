package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.localization.PyMethodsAnalyzer

class PatternBasedAutoFix(
    private val token: PyElement,
    private val holder: PyMethodsAnalyzer.PatternBasedProblemsHolder
) : LocalQuickFix {
    private val logger = Logger.getInstance(this::class.java)

    override fun getFamilyName(): String = "BigFinder: autofix using change pattern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
                PatternsSuggestionsListPopupStep(token, holder)
            )
            FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
                suggestionsPopup.showInBestPositionFor(editor)
            }
        } catch (ex: Exception) {
            logger.error(ex)
        }
    }
}