package org.jetbrains.research.ide

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.ui.DocumentAdapter
import org.jetbrains.research.common.BugFinderConfig
import org.jetbrains.research.localization.PyMethodsAnalyzer
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.reflect.KMutableProperty0

class BugFinderInspection : LocalInspectionTool() {
    override fun createOptionsPanel(): JComponent? {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        fun createTextFieldBoundToPathProperty(pathProperty: KMutableProperty0<Path>): JTextField {
            val textField = JTextField(pathProperty.get().toString(), 60).also {
                it.maximumSize = it.preferredSize
            }
            textField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) {
                    pathProperty.set(Paths.get(textField.text))
                }
            })
            return textField
        }

        val labelEnterPatternsOutputPath = JLabel("Enter path to code-change-miner output directory:")
        val textFieldPatternsOutputPath =
            createTextFieldBoundToPathProperty(BugFinderConfig::patternsOutputPath)

        val labelCodeChangeMinerPath = JLabel("Enter path to code-change-miner package:")
        val textFieldCodeChangeMinerPath =
            createTextFieldBoundToPathProperty(BugFinderConfig::codeChangeMinerPath)

        val labelPythonExecutablePath = JLabel("Enter path to python executable:")
        val textFieldPythonExecutablePath =
            createTextFieldBoundToPathProperty(BugFinderConfig::pythonExecutablePath)

        with(panel) {
            add(labelEnterPatternsOutputPath)
            add(textFieldPatternsOutputPath)
            add(labelCodeChangeMinerPath)
            add(textFieldCodeChangeMinerPath)
            add(labelPythonExecutablePath)
            add(textFieldPythonExecutablePath)
        }
        return panel
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return if (!isOnTheFly) {
            PyMethodsAnalyzer(holder)
        } else {
            super.buildVisitor(holder, isOnTheFly)
        }
    }

}