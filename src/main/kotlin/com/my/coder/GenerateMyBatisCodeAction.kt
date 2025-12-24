package com.my.coder

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

class GenerateMyBatisCodeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withFileFilter { val ext = it.extension?.lowercase(); ext == "yaml" || ext == "yml" }
        val file = FileChooser.chooseFile(descriptor, project, null)
        if (file == null) {
            Messages.showInfoMessage(project, "请选择generator.yaml配置文件", "MyBatis Generator")
            return
        }
        runGenerator(project, file)
    }

    private fun runGenerator(project: Project, file: VirtualFile) {
        try {
            Generator.run(project, file)
        } catch (t: Throwable) {
            Messages.showErrorDialog(project, t.message ?: "生成失败", "MyBatis Generator")
        }
    }
}
