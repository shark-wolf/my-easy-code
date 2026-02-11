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
import com.intellij.openapi.util.IntellijInternalApi

/**
 * 入口动作：从 IDEA Database 选中表启动生成。
 * - 收集 Database 视图选中的 DbTable
 * - 打开生成弹框 QuickGenerateDialog
 * - 按所选表执行生成
 *
 * @since 1.0.0
 * @author Neo
 */
class GenerateFromIdeaDatabaseAction : AnAction(), DumbAware {
    /**
     * 更新动作状态。
     *
     * @param e 动作事件
     * @since 1.0.0
     */
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.presentation.isVisible = true
    }
    
    /**
     * 执行动作：打开生成对话框并处理用户选择。
     *
     * @param e 动作事件
     * @since 1.0.0
     */
    @IntellijInternalApi
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tables = collectSelectedTables(e)
        if (tables.isEmpty()) {
            Messages.showInfoMessage(project, "请在Database视图中选中至少一个表", "MyBatis Generator")
            return
        }
        // 推断数据源名字/Schema 以在弹框左侧标题展示
        val dsTitle = try {
            val t = tables.firstOrNull()
            val schema = dbNameOf(t)
            if (!schema.isNullOrBlank()) schema else {
                val url = jdbcUrlOf(t?.dataSource)
                val db = if (!url.isNullOrBlank()) parseDbFromUrl(url!!) else null
                db ?: t?.dataSource?.name
            }
        } catch (_: Throwable) { null }
        val leftList = tables.map { it.name }
        val dlg = QuickGenerateDialog(project, tables.map { it.name }, dsTitle, leftList)
        if (!dlg.showAndGet()) return
        // 收集弹框配置与选择
        val cfg = dlg.config()
        val templateRoot = dlg.templateRoot()
        val selectedNames = dlg.selectedTableNames().toSet()
        val chosen = mutableListOf<DasTable>()
        val facade = DbPsiFacade.getInstance(project)
        facade.dataSources.forEach { src ->
            for (t in DasUtil.getTables(src.delegate)) {
                if (selectedNames.contains(t.name)) chosen += t
            }
        }
        runGenerator(project, cfg, templateRoot, chosen)
    }

    /**
     * 获取数据库表所属的数据源或模式名称。
     *
     * @param t 数据库表对象
     * @return 数据源或模式名称，如果无法获取则返回 null
     * @since 1.0.0
     */
    private fun dbNameOf(t: DbTable?): String? {
        if (t == null) return null
        return try {
            try {
                val mSchema = t::class.java.getMethod("getSchema")
                val schema = mSchema.invoke(t)
                if (schema != null) {
                    val mName = schema::class.java.getMethod("getName")
                    val nm = mName.invoke(schema) as? String
                    if (!nm.isNullOrBlank()) return nm
                }
            } catch (_: Throwable) { }
            var cur: Any? = t.parent
            while (cur != null) {
                val cls = cur::class.java.name
                val nameProp = try {
                    val m = cur::class.java.getMethod("getName")
                    m.invoke(cur) as? String
                } catch (_: Throwable) { null }
                if (cls == "com.intellij.database.psi.DbSchema") {
                    return nameProp
                }
                cur = try {
                    val m = cur::class.java.getMethod("getParent")
                    m.invoke(cur)
                } catch (_: Throwable) { null }
            }
            null
        } catch (_: Throwable) { null }
    }
    /**
     * 获取数据源的 JDBC URL。
     *
     * @param ds 数据源对象
     * @return JDBC URL 字符串，如果无法获取则返回 null
     * @since 1.0.0
     */
    private fun jdbcUrlOf(ds: Any?): String? {
        if (ds == null) return null
        return try {
            val m1 = ds::class.java.methods.firstOrNull { it.name == "getUrl" && it.parameterCount == 0 }
            val v1 = m1?.invoke(ds) as? String
            if (!v1.isNullOrBlank()) return v1
            val mCfg = ds::class.java.methods.firstOrNull { it.name == "getConnectionConfig" && it.parameterCount == 0 }
            val cfg = mCfg?.invoke(ds)
            if (cfg != null) {
                val m2 = cfg::class.java.methods.firstOrNull { it.name == "getUrl" && it.parameterCount == 0 }
                val v2 = m2?.invoke(cfg) as? String
                if (!v2.isNullOrBlank()) return v2
                val m3 = cfg::class.java.methods.firstOrNull { it.name == "getJdbcUrl" && it.parameterCount == 0 }
                val v3 = m3?.invoke(cfg) as? String
                if (!v3.isNullOrBlank()) return v3
            }
            null
        } catch (_: Throwable) { null }
    }
    /**
     * 从 JDBC URL 中解析数据库名称。
     *
     * @param url JDBC URL 字符串
     * @return 解析出的数据库名称，如果无法解析则返回 null
     * @since 1.0.0
     */
    private fun parseDbFromUrl(url: String): String? {
        try {
            val u = url.lowercase()
            // mysql/postgres pattern: jdbc:mysql://host:port/schema?params
            val slash = url.lastIndexOf('/')
            if (slash >= 0) {
                val tail = url.substring(slash + 1)
                val stop = tail.indexOfAny(charArrayOf('?', ';', '/'))
                val name = if (stop >= 0) tail.substring(0, stop) else tail
                if (name.isNotBlank() && name != "jdbc") return name
            }
            // sqlserver: jdbc:sqlserver://host;databaseName=xxx;...
            val keyIdx = u.indexOf("databasename=")
            if (keyIdx >= 0) {
                val rest = url.substring(keyIdx + "databasename=".length)
                val stop = rest.indexOfAny(charArrayOf(';', '?', '&'))
                val name = if (stop >= 0) rest.substring(0, stop) else rest
                if (name.isNotBlank()) return name
            }
            // oracle service/SID: jdbc:oracle:thin:@//host:port/service or :sid
            // Attempt extract last path segment
            val parts = url.split('/', ':')
            val candidate = parts.lastOrNull { it.isNotBlank() && it != "jdbc" && !it.contains("@") }
            if (!candidate.isNullOrBlank()) return candidate
        } catch (_: Throwable) {}
        return null
    }

    /**
     * 收集在 Database 视图中选中的数据库表。
     *
     * @param e 动作事件
     * @return 选中的数据库表列表
     * @since 1.0.0
     */
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

    /**
     * 运行代码生成器。
     *
     * @param project 当前项目实例
     * @param cfg 生成器配置
     * @param templateRoot 模板根目录路径
     * @param tables 要生成的数据库表列表
     * @since 1.0.0
     */
    private fun runGenerator(project: Project, cfg: com.my.coder.config.GeneratorConfig, templateRoot: java.nio.file.Path, tables: List<DasTable>) {
        try {
            Generator.runFromIdeaDatabaseConfig(project, cfg, tables, templateRoot)
        } catch (t: Throwable) {
            Messages.showErrorDialog(project, t.message ?: "生成失败", "MyBatis Generator")
        }
    }
}
