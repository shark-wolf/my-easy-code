package com.my.coder.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import javax.swing.JComponent
import java.awt.Dimension

class TemplatePreviewDialog(private val project: Project, private val fileName: String, private val content: String) : DialogWrapper(project) {
    private var editor: Editor? = null
    init {
        title = "模板预览: $fileName"
        isModal = true
        setOKButtonText("关闭")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val doc = EditorFactory.getInstance().createDocument(content)
        editor = EditorFactory.getInstance().createEditor(doc, project)
        val ex = editor as EditorEx
        val scheme = EditorColorsManager.getInstance().globalScheme
        ex.colorsScheme = scheme
        val hl = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileName)
        ex.highlighter = hl
        ex.settings.isUseSoftWraps = false
        ex.component.preferredSize = Dimension(900, 600)
        return ex.component
    }

    override fun dispose() {
        val e = editor
        if (e != null) EditorFactory.getInstance().releaseEditor(e)
        editor = null
        super.dispose()
    }
}
