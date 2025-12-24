package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import java.nio.file.Path

class TemplatePreviewNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in com.intellij.openapi.fileEditor.FileEditor, out javax.swing.JComponent?>? {
        val ok = file.extension?.lowercase() == "ftl" &&
            project.basePath != null &&
            file.path.contains("${Path.of(project.basePath!!).resolve("my-easy-code").resolve("templates")}".replace('\\', '/'))
        if (!ok) return null
        return Function { _: com.intellij.openapi.fileEditor.FileEditor ->
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
            panel.text = "预览该模板渲染后的生成内容"
            panel.createActionLabel("预览") {
                TemplatePreview.open(project, file)
            }
            panel
        }
    }
}
