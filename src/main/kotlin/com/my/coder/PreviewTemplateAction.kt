package com.my.coder

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.database.util.DasUtil
import com.intellij.database.psi.DbPsiFacade
import com.my.coder.config.ColumnMeta
import com.my.coder.config.TableMeta
import com.my.coder.settings.GeneratorSettings
import com.intellij.openapi.components.service
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.Version
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

class PreviewTemplateAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val ok = project != null && file != null && file.extension?.lowercase() == "ftl" && run {
            val base = "${Path.of(project!!.basePath ?: "").resolve("my-easy-code").resolve("templates")}".replace('\\','/')
            val fp = file.path.replace('\\','/')
            fp.contains(base)
        }
        e.presentation.isEnabledAndVisible = ok
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        TemplatePreview.open(project, file)
    }
}
