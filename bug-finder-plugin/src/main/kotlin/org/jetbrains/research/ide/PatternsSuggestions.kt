package org.jetbrains.research.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.jetbrains.research.PatternsStorage
import java.nio.file.Path

class PatternsSuggestions(private val suggestions: List<Path>) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<Path>("Patterns", suggestions) {
                override fun getTextFor(value: Path): String {
                    return PatternsStorage.getDescription(value) ?: "Unnamed pattern: $value"
                }
            }
        )
        if (editor != null) {
            suggestionsPopup.showInBestPositionFor(editor)
        }
    }

    override fun getFamilyName(): String = "BugFinder: show relevant patterns"
}