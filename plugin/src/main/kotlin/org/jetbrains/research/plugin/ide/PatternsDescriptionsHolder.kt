package org.jetbrains.research.plugin.ide

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

/**
 * A class for providing fix suggestions based on the found patterns in the IDE, without autofixes.
 */
class PatternsDescriptionsHolder(private val patternsDescriptions: HashSet<String>) : LocalQuickFix {

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val suggestionsPopup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<String>("Patterns", patternsDescriptions.toList()) {
                override fun getTextFor(description: String) = description
            }
        )
        FileEditorManager.getInstance(project)
            .selectedTextEditor
            ?.let { suggestionsPopup.showInBestPositionFor(it) }
    }

    override fun getFamilyName(): String = "BugFinder: show relevant patterns"
}