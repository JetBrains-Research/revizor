package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.IncorrectOperationException
import com.jetbrains.python.psi.PyElement
import org.jetbrains.research.plugin.PatternsStorage

class PatternBasedAutoFix(
    private val patternId: String,
    val patternPsiElementPointer: SmartPsiElementPointer<PyElement>
) : LocalQuickFix {
    private val logger = Logger.getInstance(this::class.java)

    override fun getFamilyName(): String = "BigFinder: autofix using change pattern"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        try {
            val targetPsiElement = descriptor.psiElement
            val patternPsiElement = patternPsiElementPointer.element
            val actions = PatternsStorage.getPatternEditActionsById(patternId)
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
                object : BaseListPopupStep<String>("Patterns", actions.map { it.toString() }) {
                    override fun getTextFor(description: String) = description
                }
            )
            if (editor != null) {
                suggestionsPopup.showInBestPositionFor(editor)
            }
        } catch (ex: IncorrectOperationException) {
            logger.error(ex)
        }
    }
}