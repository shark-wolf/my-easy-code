package com.my.coder

import com.intellij.database.psi.DbTable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.DasUtil
import com.intellij.database.model.DasTable

class GenerateFromIdeaDatabaseAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        val tables = collectSelectedTables(e)
        e.presentation.isEnabledAndVisible = tables.isNotEmpty()
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tables = collectSelectedTables(e)
        if (tables.isEmpty()) {
            Messages.showInfoMessage(project, "请在Database视图中选中至少一个表", "MyBatis Generator")
            return
        }
        val dlg = QuickGenerateDialog(project, tables.map { it.name })
        if (!dlg.showAndGet()) return
        val cfg = dlg.config()
        val templateRoot = dlg.templateRoot()
        val selectedNames = dlg.selectedTableNames().toSet()
        val chosen = mutableListOf<DasTable>()
        val facade = DbPsiFacade.getInstance(project)
        facade.dataSources.forEach { src ->
            for (t in DasUtil.getTables(src.delegate)) if (selectedNames.contains(t.name)) chosen += t
        }
        runGenerator(project, cfg, templateRoot, chosen)
    }

    private fun collectSelectedTables(e: AnActionEvent): List<DbTable> {
        val list = mutableListOf<DbTable>()
        val nav = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        if (nav != null) {
            nav.forEach {
                if (it is DbTable) list += it
            }
        }
        val psi = e.getData(com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY)
        if (psi != null) {
            psi.forEach {
                if (it is DbTable) list += it
            }
        }
        return list.distinctBy { it.name }
    }

    private fun runGenerator(project: Project, cfg: com.my.coder.config.GeneratorConfig, templateRoot: java.nio.file.Path, tables: List<DasTable>) {
        try {
            Generator.runFromIdeaDatabaseConfig(project, cfg, tables, templateRoot)
        } catch (t: Throwable) {
            Messages.showErrorDialog(project, t.message ?: "生成失败", "MyBatis Generator")
        }
    }
}
