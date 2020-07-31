package org.jetbrains.research

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.ui.DocumentAdapter
import com.jetbrains.python.psi.PyElementVisitor
import javax.swing.*
import javax.swing.event.DocumentEvent

class BugFinderInspection : LocalInspectionTool() {
    private var patternsOutputPath = "/home/oleg/prog/jetbrains/data/relaunch_output"
    private var codeChangeMinerPath = "/home/oleg/prog/jetbrains/"

    override fun createOptionsPanel(): JComponent? {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val labelEnterPatternsOutputPath = JLabel("Enter path to code-change-miner output directory:")
        val textFieldPatternsOutputPath = JTextField(patternsOutputPath, 60).also {
            it.maximumSize = it.preferredSize
        }
        textFieldPatternsOutputPath.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                patternsOutputPath = textFieldPatternsOutputPath.text
            }
        })

        val labelCodeChangeMinerPath = JLabel("Enter path to code-change-miner package:")
        val textFieldCodeChangeMinerPath = JTextField(codeChangeMinerPath, 60).also {
            it.maximumSize = it.preferredSize
        }
        textFieldCodeChangeMinerPath.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                codeChangeMinerPath = textFieldCodeChangeMinerPath.text
            }
        })

        with(panel) {
            add(Box.createVerticalGlue())
            add(labelEnterPatternsOutputPath)
            add(textFieldPatternsOutputPath)
            add(Box.createVerticalGlue())
            add(labelCodeChangeMinerPath)
            add(textFieldCodeChangeMinerPath)
            add(Box.createVerticalGlue())
        }
        return panel
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PyElementVisitor() {}
    }

}