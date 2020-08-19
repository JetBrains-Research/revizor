package org.jetbrains.research.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.jetbrains.research.PatternsStorage

class PatternsSuggestions(private val patternsIds: List<String>) : LocalQuickFix {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>("Patterns", patternsIds) {
                override fun getTextFor(patternId: String): String {
                    return PatternsStorage.getPatternDescriptionById(patternId) ?: "Unnamed pattern: $patternId"
                }
            }
        )
        if (editor != null) {
            suggestionsPopup.showInBestPositionFor(editor)
        }
    }

    override fun getFamilyName(): String = "BugFinder: show relevant patterns"
}