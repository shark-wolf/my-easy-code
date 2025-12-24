package com.my.coder.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import java.awt.BorderLayout
import java.awt.Font

class TemplatePreviewDialog(
    project: Project,
    private val fileName: String,
    private val content: String
) : DialogWrapper(project) {
    init {
        title = "预览: $fileName"
        setInitialLocationCallback {
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            java.awt.Point((screen.width - 900) / 2, (screen.height - 600) / 2)
        }
        init()
    }
    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(8, 8))
        val header = JBLabel(fileName)
        panel.add(header, BorderLayout.NORTH)
        val area = JTextArea(content)
        area.isEditable = false
        area.font = Font(Font.MONOSPACED, Font.PLAIN, area.font.size)
        val sp = JBScrollPane(area)
        panel.add(sp, BorderLayout.CENTER)
        panel.preferredSize = java.awt.Dimension(900, 600)
        return panel
    }
    override fun createActions(): Array<javax.swing.Action> {
        return arrayOf(cancelAction)
    }
}
