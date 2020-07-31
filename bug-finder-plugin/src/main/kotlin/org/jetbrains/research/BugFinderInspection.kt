package org.jetbrains.research

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.ui.DocumentAdapter
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyElementVisitor
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.*
import javax.swing.event.DocumentEvent
import kotlin.reflect.KMutableProperty0

class BugFinderInspection : LocalInspectionTool() {
    companion object {
        var patternsOutputPath: Path = Paths.get("/home/oleg/prog/jetbrains/data/relaunch_output")
        var codeChangeMinerPath: Path = Paths.get("/home/oleg/prog/jetbrains/bug-finder/code_change-miner")
        var pythonExecutablePath: Path = Paths.get("/home/oleg/miniconda3/envs/bug-finder/bin/python")
    }

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
        val textFieldPatternsOutputPath = createTextFieldBoundToPathProperty(Companion::patternsOutputPath)

        val labelCodeChangeMinerPath = JLabel("Enter path to code-change-miner package:")
        val textFieldCodeChangeMinerPath = createTextFieldBoundToPathProperty(Companion::codeChangeMinerPath)

        val labelPythonExecutablePath = JLabel("Enter path to python executable:")
        val textFieldPythonExecutablePath = createTextFieldBoundToPathProperty(Companion::pythonExecutablePath)

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
        return object : PyElementVisitor() {
            override fun visitPyBinaryExpression(node: PyBinaryExpression?) {
                super.visitPyBinaryExpression(node)
                if (node != null) {
                    holder.registerProblem(node.originalElement, "ALERT")
                }
            }
        }
    }

}