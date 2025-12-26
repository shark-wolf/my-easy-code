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
        e.presentation.isEnabled = true
        e.presentation.isVisible = true
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tables = collectSelectedTables(e)
        if (tables.isEmpty()) {
            Messages.showInfoMessage(project, "请在Database视图中选中至少一个表", "MyBatis Generator")
            return
        }
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
    private fun jdbcCredsOf(ds: Any?): Pair<String?, String?>? {
        if (ds == null) return null
        return try {
            val mCfg = ds::class.java.methods.firstOrNull { it.name == "getConnectionConfig" && it.parameterCount == 0 }
            val cfg = mCfg?.invoke(ds)
            if (cfg != null) {
                val mu = cfg::class.java.methods.firstOrNull { it.name.lowercase() in setOf("getuser","getusername","getusername") && it.parameterCount == 0 }
                val mp = cfg::class.java.methods.firstOrNull { it.name.lowercase() in setOf("getpassword") && it.parameterCount == 0 }
                val u = mu?.invoke(cfg) as? String
                val p = mp?.invoke(cfg) as? String
                return u to p
            }
            null
        } catch (_: Throwable) { null }
    }
    private fun listTablesBySql(url: String, user: String?, pass: String?, schema: String?): List<String>? {
        return try {
            val conn = if (!user.isNullOrBlank()) java.sql.DriverManager.getConnection(url, user, pass ?: "") else java.sql.DriverManager.getConnection(url)
            val lower = url.lowercase()
            val sql = when {
                lower.contains("mysql") -> "SHOW TABLES"
                lower.contains("postgresql") || lower.contains("postgres") ->
                    if (!schema.isNullOrBlank()) "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = '$schema'"
                    else "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')"
                lower.contains("sqlserver") -> "SELECT name FROM sys.tables"
                lower.contains("oracle") ->
                    if (!schema.isNullOrBlank()) "SELECT table_name FROM all_tables WHERE owner = '$schema'"
                    else "SELECT table_name FROM user_tables"
                lower.contains("sqlite") -> "SELECT name FROM sqlite_master WHERE type='table'"
                else -> null
            }
            val names = mutableListOf<String>()
            conn.use { c ->
                if (sql != null) {
                    c.createStatement().use { st ->
                        val rs = st.executeQuery(sql)
                        rs.use {
                            while (rs.next()) {
                                val v1 = try { rs.getString(1) } catch (_: Throwable) { null }
                                val vTab = try { rs.getString("TABLE_NAME") } catch (_: Throwable) { null }
                                val name = v1 ?: vTab
                                if (!name.isNullOrBlank()) names += name
                            }
                        }
                    }
                } else {
                    val sch = if (schema.isNullOrBlank()) null else schema
                    c.metaData.getTables(null, sch, "%", arrayOf("TABLE")).use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("TABLE_NAME")
                            if (!name.isNullOrBlank()) names += name
                        }
                    }
                }
            }
            names.sorted()
        } catch (_: Throwable) { null }
    }
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
