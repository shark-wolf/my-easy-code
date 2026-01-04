package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import java.nio.file.Path

class TemplatePreviewNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out javax.swing.JComponent>? {
        if (!shouldShow(project, file)) return null
        return Function { _: FileEditor ->
            val panel = EditorNotificationPanel()
            panel.text = "预览模板生成效果"
            panel.createActionLabel("预览") {
                try { TemplatePreview.open(project, file) } catch (_: Throwable) {}
            }
            panel
        }
    }
    private fun shouldShow(project: Project, file: VirtualFile): Boolean {
        if (file.extension?.lowercase() != "ftl") return false
        val base = project.basePath ?: return false
        val p = file.path.replace('\\','/')
        val t1 = Path.of(base).resolve("my-easy-code").resolve("templates").toString().replace('\\','/')
        val t2 = Path.of(base).resolve("src/main/resources").resolve("templates").toString().replace('\\','/')
        val t3 = Path.of(base).resolve("templates").toString().replace('\\','/')
        return p.contains(t1) || p.contains(t2) || p.contains(t3)
    }
}

