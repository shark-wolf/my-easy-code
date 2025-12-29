package com.my.coder

import com.my.coder.config.ColumnMeta
import com.my.coder.config.GeneratorConfig
import com.my.coder.config.TableMeta
import com.my.coder.config.TemplateItem
import com.my.coder.type.TypeMapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.Version
import org.yaml.snakeyaml.Yaml
import com.my.coder.settings.GeneratorSettings
import com.intellij.openapi.components.service
import com.intellij.database.psi.DbTable
import com.intellij.database.model.DasTable
import com.intellij.database.util.DasUtil
import com.intellij.database.psi.DbPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

/**
 * 代码生成核心入口：负责从 YAML/IDEA 数据源解析表结构、合并 mapper.yml 的类型映射/枚举定义，
 * 渲染 Freemarker 模板并输出到指定路径，同时进行 PSI 提交与格式化，并收集错误信息。
 */
object Generator {
    fun run(project: Project, yamlFile: VirtualFile) {
        run(project, yamlFile, null)
    }

    /**
     * 按 YAML 配置执行生成，可选指定覆盖的表名单（只生成这些表）。
     */
    fun run(project: Project, yamlFile: VirtualFile, overrideTables: List<String>?) {
        val text = VfsUtil.loadText(yamlFile)
        val map = Yaml().load<Map<String, Any>>(text)
        val config = toConfig(map)
        val s = project.service<GeneratorSettings>().state
        val cfgEff = run {
            val effDto = s.dtoExclude?.toList() ?: config.dtoExclude
            val effVo = s.voExclude?.toList() ?: config.voExclude
            val activeTemplates = s.schemes?.firstOrNull { it.name == s.activeScheme }?.templates ?: config.templates
            config.copy(
                packageName = s.packageName ?: config.packageName,
                baseDir = s.baseDir ?: config.baseDir,
                author = s.author ?: config.author,
                templates = activeTemplates,
                tables = config.tables,
                dtoExclude = effDto,
                voExclude = effVo,
                generateMapper = s.generateMapper,
                generateMapperXml = s.generateMapperXml
            )
        }
        // 未在 YAML 配置数据库时，回退使用 IDEA Database 的数据源
        val useIdeaDb = config.database.driver.isBlank() || config.database.url.isBlank()
        val baseDirEff = (cfgEff.baseDir ?: project.basePath ?: "")
        val mapperRoot = project.basePath?.let { Path.of(it).resolve("my-easy-code").resolve("mapper") }
        val tableColumnType = mutableMapOf<Pair<String, String>, String>()
        data class GenType(val table: String, val out: String, val pkg: String, val name: String, val isEnum: Boolean, val enumValues: List<String>)
        val classDefs = mutableListOf<GenType>()
        // 读取 my-easy-code/mapper/mapper.yml，合并列类型映射与枚举生成任务
        if (mapperRoot != null && cfgEff.applyMapperMapping) {
            val mapperFile = mapperRoot.resolve("mapper.yml")
            if (Files.exists(mapperFile)) {
                try {
                    val mAny = Yaml().load<Any>(Files.readString(mapperFile))
                    val entries: List<Any> = when (mAny) {
                        is Map<*, *> -> (mAny["mappings"] as? List<*>)?.filterNotNull() ?: listOf(mAny)
                        is List<*> -> mAny.filterNotNull()
                        else -> emptyList()
                    }
                    entries.forEach { e ->
                        val em = e as? Map<*, *> ?: return@forEach
                        val tName = em["tableName"]?.toString() ?: return@forEach
                        val col = em["column"]?.toString() ?: return@forEach
                        val jm = em["Java"] as? Map<*, *> ?: return@forEach
                        val type = jm["type"]?.toString()
                        val name = jm["name"]?.toString()
                        val savePath = jm["savePath"]?.toString()
                        val enumVals = run {
                            val e = jm["enum"]
                            val direct = jm["values"]
                            val listAny = when (e) {
                                is Map<*, *> -> (e["values"] as? List<*>)
                                is List<*> -> e
                                else -> null
                            } ?: (direct as? List<*>)
                            listAny?.map { it.toString() } ?: emptyList()
                        }
                        if (!name.isNullOrBlank() && !savePath.isNullOrBlank()) {
                            var p = savePath
                            val pkgPath = cfgEff.packageName.replace('.', '/')
                            p = p.replace("\${baseDir}", baseDirEff)
                            p = p.replace("\${projectBase}", baseDirEff)
                            p = p.replace("\${packagePath}", pkgPath)
                            p = p.replace("\${packageName}", cfgEff.packageName)
                            val out = p
                            val pkg = run {
                                val marker = "src/main/java/"
                                val idx = out.indexOf(marker)
                                if (idx >= 0) {
                                    val rel = out.substring(idx + marker.length)
                                    val dir = rel.substringBeforeLast('/')
                                    dir.replace('/', '.')
                                } else cfgEff.packageName
                            }
                            if (type == "enum") tableColumnType[tName to col] = "$pkg.$name" else if (!type.isNullOrBlank()) tableColumnType[tName to col] = type
                            classDefs += GenType(tName, out, pkg, name, type == "enum", enumVals)
                        }
                        else if (!type.isNullOrBlank() && type != "enum") {
                            tableColumnType[tName to col] = type
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
        // 使用 IDEA Database 源执行生成
        if (useIdeaDb) {
            val sources = DbPsiFacade.getInstance(project).dataSources
            val selectedNames = overrideTables
            val tables = mutableListOf<TableMeta>()
            sources.forEach { src ->
                for (t in DasUtil.getTables(src.delegate)) {
                    val name = t.name
                    if (selectedNames != null && selectedNames.isNotEmpty() && !selectedNames.contains(name)) continue
                    if (cfgEff.tables.exclude != null && cfgEff.tables.exclude.contains(name)) continue
                    if (cfgEff.tables.include != null && cfgEff.tables.include.isNotEmpty() && !cfgEff.tables.include.contains(name)) continue
                    val cols = mutableListOf<ColumnMeta>()
                    for (c in DasUtil.getColumns(t)) {
                        val typeName = c.dataType.typeName
                        val size = c.dataType.size
                        val nullable = !c.isNotNull
                        val primary = DasUtil.isPrimary(c)
                        val mapped = tableColumnType[name to c.name]
                        val javaType = mapped ?: resolveJavaType(typeName, c.name, size, nullable, cfgEff)
                        val comment = try { c.comment } catch (_: Throwable) { null }
                        cols += ColumnMeta(c.name, typeName, nullable, primary, javaType, toCamelLower(c.name), size, comment)
                    }
                    val tComment = try { t.comment } catch (_: Throwable) { null }
                    val strip = cfgEff.stripTablePrefix
                    val baseName = if (!strip.isNullOrBlank() && name.startsWith(strip)) name.removePrefix(strip) else name
                    tables += TableMeta(name, toCamelUpper(baseName), cols, tComment)
                }
            }
            val yamlDir = VfsUtil.virtualToIoFile(yamlFile).toPath().parent
            val templateBase = project.service<GeneratorSettings>().state.templateRoot?.let { Path.of(it) } ?: yamlDir
            val tableSet = tables.map { it.name }.toSet()
            classDefs.filter { tableSet.contains(it.table) }.forEach { g ->
                try {
                    val path = Path.of(g.out)
                    Files.createDirectories(path.parent)
                    val content = if (g.isEnum) {
                        val fm = Configuration(Version("2.3.31"))
                        fm.defaultEncoding = "UTF-8"
                        val candidates = mutableListOf<Path>()
                        candidates.add(templateBase.resolve("enum.ftl"))
                        val baseP = project.basePath
                        if (baseP != null) {
                            candidates.add(Path.of(baseP).resolve("src/main/resources").resolve("templates/enum.ftl"))
                            candidates.add(Path.of(baseP).resolve("my-easy-code").resolve("templates/enum.ftl"))
                            candidates.add(Path.of(baseP).resolve("templates/enum.ftl"))
                        }
                        val enumPath = candidates.firstOrNull { Files.exists(it) }
                        val tplText = if (enumPath != null) Files.readString(enumPath) else "package ${'$'}{enumPackage};\npublic enum ${'$'}{enumName} {\n\n}\n"
                        val tpl = Template("enum", StringReader(tplText), fm)
                        val data = mapOf("enumPackage" to g.pkg, "enumName" to g.name, "values" to g.enumValues)
                        val sb = java.io.StringWriter()
                        tpl.process(data, sb)
                        sb.toString()
                    } else {
                        "package ${'$'}{g.pkg};\npublic class ${'$'}{g.name} {}\n"
                    }
                    Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())?.refresh(false, true)
                } catch (_: Throwable) {}
            }
            generate(project, cfgEff, tables, templateBase)
        } else {
            // 使用 JDBC 元数据执行生成
            Class.forName(config.database.driver)
            val props = Properties()
            props["user"] = config.database.username
            props["password"] = config.database.password
            DriverManager.getConnection(config.database.url, props).use { conn ->
                val meta = conn.metaData
                val tableNames = overrideTables ?: resolveTables(meta.catalogs, meta.schemas, cfgEff.tables, conn)
                val tables = tableNames.map { name ->
                    val cols = mutableListOf<ColumnMeta>()
                    val pk = primaryKeys(meta, name)
                    meta.getColumns(null, null, name, null).use { rs ->
                        while (rs.next()) {
                            val cName = rs.getString("COLUMN_NAME")
                            val type = rs.getString("TYPE_NAME")
                            val size = rs.getInt("COLUMN_SIZE")
                            val nullable = rs.getInt("NULLABLE") == 1
                            val primary = pk.contains(cName)
                            val mapped = tableColumnType[name to cName]
                            val javaType = mapped ?: resolveJavaType(type, cName, size, nullable, cfgEff)
                            val comment = try { rs.getString("REMARKS") } catch (_: Throwable) { null }
                            cols += ColumnMeta(cName, type, nullable, primary, javaType, toCamelLower(cName), size, comment)
                        }
                    }
                    var tComment: String? = null
                    meta.getTables(null, null, name, null).use { trs ->
                        while (trs.next()) tComment = try { trs.getString("REMARKS") } catch (_: Throwable) { null }
                    }
                    val strip = cfgEff.stripTablePrefix
                    val baseName = if (!strip.isNullOrBlank() && name.startsWith(strip)) name.removePrefix(strip) else name
                    TableMeta(name, toCamelUpper(baseName), cols, tComment)
                }
                val yamlDir = VfsUtil.virtualToIoFile(yamlFile).toPath().parent
                val templateBase = project.service<GeneratorSettings>().state.templateRoot?.let { Path.of(it) } ?: yamlDir
                val tableSet2 = tables.map { it.name }.toSet()
                classDefs.filter { tableSet2.contains(it.table) }.forEach { g ->
                    try {
                        val path = Path.of(g.out)
                        Files.createDirectories(path.parent)
                        val content = if (g.isEnum) {
                            val fm = Configuration(Version("2.3.31"))
                            fm.defaultEncoding = "UTF-8"
                            val candidates = mutableListOf<Path>()
                            candidates.add(templateBase.resolve("enum.ftl"))
                            val baseP = project.basePath
                            if (baseP != null) {
                                candidates.add(Path.of(baseP).resolve("src/main/resources").resolve("templates/enum.ftl"))
                                candidates.add(Path.of(baseP).resolve("my-easy-code").resolve("templates/enum.ftl"))
                                candidates.add(Path.of(baseP).resolve("templates/enum.ftl"))
                            }
                            val enumPath = candidates.firstOrNull { Files.exists(it) }
                            val tplText = if (enumPath != null) Files.readString(enumPath) else "package ${'$'}{enumPackage};\npublic enum ${'$'}{enumName} {\n\n}\n"
                            val tpl = Template("enum", StringReader(tplText), fm)
                            val data = mapOf("enumPackage" to g.pkg, "enumName" to g.name, "values" to g.enumValues)
                            val sb = java.io.StringWriter()
                            tpl.process(data, sb)
                            sb.toString()
                        } else {
                            "package ${'$'}{g.pkg};\npublic class ${'$'}{g.name} {}\n"
                        }
                        Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())?.refresh(false, true)
                    } catch (_: Throwable) {}
                }
                generate(project, cfgEff, tables, templateBase)
            }
        }
    }

    /**
     * IDEA 数据库视图右键入口：直接传入 DbTable 列表执行生成。
     */
    fun runFromIdeaDatabase(project: Project, yamlFile: VirtualFile, dbTables: List<DbTable>) {
        val text = VfsUtil.loadText(yamlFile)
        val map = Yaml().load<Map<String, Any>>(text)
        val config = toConfig(map)
        val s2 = project.service<GeneratorSettings>().state
        val cfgEff = run {
            val effDto = s2.dtoExclude?.toList() ?: config.dtoExclude
            val effVo = s2.voExclude?.toList() ?: config.voExclude
            val activeTemplates = s2.schemes?.firstOrNull { it.name == s2.activeScheme }?.templates ?: config.templates
            config.copy(
                packageName = s2.packageName ?: config.packageName,
                baseDir = s2.baseDir ?: config.baseDir,
                author = s2.author ?: config.author,
                templates = activeTemplates,
                tables = config.tables,
                dtoExclude = effDto,
                voExclude = effVo
            )
        }
        val mapperRoot = project.basePath?.let { Path.of(it).resolve("my-easy-code").resolve("mapper") }
        val tableColumnType = mutableMapOf<Pair<String, String>, String>()
        data class GenType(val table: String, val out: String, val pkg: String, val name: String, val isEnum: Boolean, val enumValues: List<String>)
        val classDefs = mutableListOf<GenType>()
        if (mapperRoot != null && cfgEff.applyMapperMapping) {
            val mapperFile = mapperRoot.resolve("mapper.yml")
            if (Files.exists(mapperFile)) {
                try {
                    val mAny = Yaml().load<Any>(Files.readString(mapperFile))
                    val entries: List<Any> = when (mAny) {
                        is Map<*, *> -> (mAny["mappings"] as? List<*>)?.filterNotNull() ?: listOf(mAny)
                        is List<*> -> mAny.filterNotNull()
                        else -> emptyList()
                    }
                    entries.forEach { e ->
                        val em = e as? Map<*, *> ?: return@forEach
                        val tName = em["tableName"]?.toString() ?: return@forEach
                        val col = em["column"]?.toString() ?: return@forEach
                        val jm = em["Java"] as? Map<*, *> ?: return@forEach
                        val type = jm["type"]?.toString()
                        if (!type.isNullOrBlank()) tableColumnType[tName to col] = type
                        val name = jm["name"]?.toString()
                        val savePath = jm["savePath"]?.toString()
                        val enumVals = run {
                            val e2 = jm["enum"]
                            val direct = jm["values"]
                            val listAny = when (e2) {
                                is Map<*, *> -> (e2["values"] as? List<*>)
                                is List<*> -> e2
                                else -> null
                            } ?: (direct as? List<*>)
                            listAny?.map { it.toString() } ?: emptyList()
                        }
                        if (!name.isNullOrBlank() && !savePath.isNullOrBlank()) {
                            val baseDirEff = (cfgEff.baseDir ?: project.basePath ?: "")
                            var p = savePath
                            val pkgPath = cfgEff.packageName.replace('.', '/')
                            p = p.replace("\${baseDir}", baseDirEff)
                            p = p.replace("\${projectBase}", baseDirEff)
                            p = p.replace("\${packagePath}", pkgPath)
                            p = p.replace("\${packageName}", cfgEff.packageName)
                            val out = p
                            val pkg = run {
                                val marker = "src/main/java/"
                                val idx = out.indexOf(marker)
                                if (idx >= 0) {
                                    val rel = out.substring(idx + marker.length)
                                    val dir = rel.substringBeforeLast('/')
                                    dir.replace('/', '.')
                                } else cfgEff.packageName
                            }
                            classDefs += GenType(tName, out, pkg, name!!, (jm["type"]?.toString() == "enum"), enumVals)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
        val tables = dbTables.map { t ->
            val cols = mutableListOf<ColumnMeta>()
            for (c in DasUtil.getColumns(t)) {
                val typeName = c.dataType.typeName
                val size = c.dataType.size
                val nullable = !c.isNotNull
                val primary = DasUtil.isPrimary(c)
                val mapped = tableColumnType[t.name to c.name]
                val javaType = mapped ?: resolveJavaType(typeName, c.name, size, nullable, cfgEff)
                val comment = try { c.comment } catch (_: Throwable) { null }
                cols += ColumnMeta(c.name, typeName, nullable, primary, javaType, toCamelLower(c.name), size, comment)
            }
            val tComment = try { t.comment } catch (_: Throwable) { null }
            TableMeta(t.name, toCamelUpper(t.name), cols, tComment)
        }
        val yamlDir = VfsUtil.virtualToIoFile(yamlFile).toPath().parent
        val templateBase = project.service<GeneratorSettings>().state.templateRoot?.let { Path.of(it) } ?: yamlDir
        val tableSet3 = tables.map { it.name }.toSet()
        classDefs.filter { tableSet3.contains(it.table) }.forEach { g ->
            try {
                val path = Path.of(g.out)
                Files.createDirectories(path.parent)
                val content = if (g.isEnum) {
                    val fm = Configuration(Version("2.3.31"))
                    fm.defaultEncoding = "UTF-8"
                    val candidates = mutableListOf<Path>()
                    candidates.add(templateBase.resolve("enum.ftl"))
                    val baseP = project.basePath
                    if (baseP != null) {
                        candidates.add(Path.of(baseP).resolve("src/main/resources").resolve("templates/enum.ftl"))
                        candidates.add(Path.of(baseP).resolve("my-easy-code").resolve("templates/enum.ftl"))
                        candidates.add(Path.of(baseP).resolve("templates/enum.ftl"))
                    }
                    val enumPath = candidates.firstOrNull { Files.exists(it) }
                    val tplText = if (enumPath != null) Files.readString(enumPath) else "package ${'$'}{enumPackage};\npublic enum ${'$'}{enumName} {\n\n}\n"
                    val tpl = Template("enum", StringReader(tplText), fm)
                    val data = mapOf("enumPackage" to g.pkg, "enumName" to g.name, "values" to g.enumValues)
                    val sb = java.io.StringWriter()
                    tpl.process(data, sb)
                    sb.toString()
                } else {
                    "package ${'$'}{g.pkg};\npublic class ${'$'}{g.name} {}\n"
                }
                Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())?.refresh(false, true)
            } catch (_: Throwable) {}
        }
        generate(project, cfgEff, tables, templateBase)
    }

    /**
     * 以现有配置和模板根目录，针对 IDEA 的 DAS 表执行生成（无 YAML 读取）。
     */
    fun runFromIdeaDatabaseConfig(project: Project, cfg: GeneratorConfig, dbTables: List<DasTable>, templateBase: Path) {
        val mapperRoot = project.basePath?.let { Path.of(it).resolve("my-easy-code").resolve("mapper") }
        val tableColumnType = mutableMapOf<Pair<String, String>, String>()
        data class GenType(val table: String, val out: String, val pkg: String, val name: String, val isEnum: Boolean, val enumValues: List<String>)
        val classDefs = mutableListOf<GenType>()
        if (mapperRoot != null && cfg.applyMapperMapping) {
            val mapperFile = mapperRoot.resolve("mapper.yml")
            if (Files.exists(mapperFile)) {
                try {
                    val mAny = Yaml().load<Any>(Files.readString(mapperFile))
                    val entries: List<Any> = when (mAny) {
                        is Map<*, *> -> (mAny["mappings"] as? List<*>)?.filterNotNull() ?: listOf(mAny)
                        is List<*> -> mAny.filterNotNull()
                        else -> emptyList()
                    }
                    entries.forEach { e ->
                        val em = e as? Map<*, *> ?: return@forEach
                        val tName = em["tableName"]?.toString() ?: return@forEach
                        val col = em["column"]?.toString() ?: return@forEach
                        val jm = em["Java"] as? Map<*, *> ?: return@forEach
                        val type = jm["type"]?.toString()
                        if (!type.isNullOrBlank()) tableColumnType[tName to col] = type
                        val name = jm["name"]?.toString()
                        val savePath = jm["savePath"]?.toString()
                        val enumVals = run {
                            val e2 = jm["enum"]
                            val direct = jm["values"]
                            val listAny = when (e2) {
                                is Map<*, *> -> (e2["values"] as? List<*>)
                                is List<*> -> e2
                                else -> null
                            } ?: (direct as? List<*>)
                            listAny?.map { it.toString() } ?: emptyList()
                        }
                        if (!name.isNullOrBlank() && !savePath.isNullOrBlank()) {
                            val baseDirEff = (cfg.baseDir ?: project.basePath ?: "")
                            var p = savePath
                            val pkgPath = cfg.packageName.replace('.', '/')
                            p = p.replace("\${baseDir}", baseDirEff)
                            p = p.replace("\${projectBase}", baseDirEff)
                            p = p.replace("\${packagePath}", pkgPath)
                            p = p.replace("\${packageName}", cfg.packageName)
                            val out = p
                            val pkg = run {
                                val marker = "src/main/java/"
                                val idx = out.indexOf(marker)
                                if (idx >= 0) {
                                    val rel = out.substring(idx + marker.length)
                                    val dir = rel.substringBeforeLast('/')
                                    dir.replace('/', '.')
                                } else cfg.packageName
                            }
                            classDefs += GenType(tName, out, pkg, name!!, (jm["type"]?.toString() == "enum"), enumVals)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
        val tables = dbTables.map { t ->
            val cols = mutableListOf<ColumnMeta>()
            for (c in com.intellij.database.util.DasUtil.getColumns(t)) {
                val typeName = c.dataType.typeName
                val size = c.dataType.size
                val nullable = !c.isNotNull
                val primary = com.intellij.database.util.DasUtil.isPrimary(c)
                val mapped = tableColumnType[t.name to c.name]
                val javaType = mapped ?: resolveJavaType(typeName, c.name, size ?: 0, nullable, cfg)
                val comment = try { c.comment } catch (_: Throwable) { null }
                cols += ColumnMeta(c.name, typeName, nullable, primary, javaType, toCamelLower(c.name), size, comment)
            }
            val tComment = try { t.comment } catch (_: Throwable) { null }
            val strip = cfg.stripTablePrefix
            val baseName = if (!strip.isNullOrBlank() && t.name.startsWith(strip)) t.name.removePrefix(strip) else t.name
            TableMeta(t.name, toCamelUpper(baseName), cols, tComment)
        }
        val tableSet4 = tables.map { it.name }.toSet()
        classDefs.filter { tableSet4.contains(it.table) }.forEach { g ->
            try {
                val path = Path.of(g.out)
                Files.createDirectories(path.parent)
                val content = if (g.isEnum) {
                    val fm = Configuration(Version("2.3.31"))
                    fm.defaultEncoding = "UTF-8"
                    val candidates = mutableListOf<Path>()
                    candidates.add(templateBase.resolve("enum.ftl"))
                    val baseP = project.basePath
                    if (baseP != null) {
                        candidates.add(Path.of(baseP).resolve("src/main/resources").resolve("templates/enum.ftl"))
                        candidates.add(Path.of(baseP).resolve("my-easy-code").resolve("templates/enum.ftl"))
                        candidates.add(Path.of(baseP).resolve("templates/enum.ftl"))
                    }
                    val enumPath = candidates.firstOrNull { Files.exists(it) }
                    val tplText = if (enumPath != null) Files.readString(enumPath) else "package ${'$'}{enumPackage};\npublic enum ${'$'}{enumName} {\n\n}\n"
                    val tpl = Template("enum", StringReader(tplText), fm)
                    val data = mapOf("enumPackage" to g.pkg, "enumName" to g.name, "values" to g.enumValues)
                    val sb = java.io.StringWriter()
                    tpl.process(data, sb)
                    sb.toString()
                } else {
                    "package ${'$'}{g.pkg};\npublic class ${'$'}{g.name} {}\n"
                }
                Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())?.refresh(false, true)
            } catch (_: Throwable) {}
        }
        generate(project, cfg, tables, templateBase)
    }

    /**
     * 解析列对应的 Java 类型：优先按列覆盖、再按数据库类型覆盖，最后回退默认映射。
     */
    private fun resolveJavaType(typeName: String, columnName: String, size: Int, nullable: Boolean, cfg: GeneratorConfig): String {
        val byColumn = cfg.columnTypeOverride?.get(columnName)
        if (byColumn != null && byColumn.isNotBlank()) return byColumn
        val byType = cfg.typeOverride?.get(typeName.lowercase()) ?: cfg.typeOverride?.get(typeName)
        if (byType != null && byType.isNotBlank()) return byType
        return TypeMapper.toJavaType(typeName, size, nullable)
    }

    /**
     * 将 YAML 映射转换为生成配置对象，仅读取核心字段。
     */
    private fun toConfig(m: Map<String, Any>): GeneratorConfig {
        val dbm = m["database"] as? Map<*, *>
        val database = if (dbm != null) com.my.coder.config.Database(
            dbm["url"]?.toString() ?: "",
            dbm["username"]?.toString() ?: "",
            dbm["password"]?.toString() ?: "",
            dbm["driver"]?.toString() ?: ""
        ) else com.my.coder.config.Database("", "", "", "")
        val packageName = m["packageName"].toString()
        val baseDir = m["baseDir"]?.toString()
        val templates = (m["templates"] as List<*>).map {
            val t = it as Map<*, *>
            TemplateItem(
                t["name"].toString(),
                t["engine"].toString(),
                t["content"]?.toString(),
                t["file"]?.toString(),
                t["outputPath"].toString(),
                (t["fileType"]?.toString() ?: "java")
            )
        }
        val ts = m["tables"] as? Map<*, *>
        val include = (ts?.get("include") as? List<*>)?.map { it.toString() }
        val exclude = (ts?.get("exclude") as? List<*>)?.map { it.toString() }
        val tables = com.my.coder.config.Tables(include, exclude)
        val author = m["author"]?.toString()
        val dtoExclude = (m["dtoExclude"] as? List<*>)?.map { it.toString() }
        val voExclude = (m["voExclude"] as? List<*>)?.map { it.toString() }
        return GeneratorConfig(database, packageName, baseDir, templates, tables, author, dtoExclude, voExclude)
    }

    /**
     * 解析 JDBC 库中的所有表，支持 include/exclude 过滤。
     */
    private fun resolveTables(catalogs: java.sql.ResultSet, schemas: java.sql.ResultSet, tables: com.my.coder.config.Tables, conn: java.sql.Connection): List<String> {
        var names = mutableListOf<String>()
        if (tables.include != null && tables.include.isNotEmpty()) return tables.include
        conn.metaData.getTables(null, null, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) {
                val name = rs.getString("TABLE_NAME")
                names += name
            }
        }
        return if (tables.exclude != null) names.filterNot { tables.exclude.contains(it) } else names
    }

    /**
     * 获取主键列集合。
     */
    private fun primaryKeys(meta: java.sql.DatabaseMetaData, table: String): Set<String> {
        var set = mutableSetOf<String>()
        meta.getPrimaryKeys(null, null, table).use { rs ->
            while (rs.next()) set += rs.getString("COLUMN_NAME")
        }
        return set
    }

    /**
     * 渲染并写出所有模板文件：动态导入、路径占位符展开、错误收集、VFS 刷新与 PSI 格式化。
     */
    private fun generate(project: Project, cfg: GeneratorConfig, tables: List<TableMeta>, templateBase: java.nio.file.Path) {
        // 计算基础输出目录与生效配置（不再合并 mapper.yml）
        val base = cfg.baseDir ?: project.basePath ?: ""
        val cfgEff = cfg
        val enumSelMap = cfgEff.tableEnumFields ?: emptyMap()
        val tablesAdj = tables.map { t ->
            val selectedCols = enumSelMap[t.name] ?: emptyList()
            val cols2 = t.columns.map { c ->
                if (selectedCols.contains(c.name)) {
                    val enumName = "${t.entityName}${toCamelUpper(c.name)}Enum"
                    val fqn = "${cfgEff.packageName}.enums.$enumName"
                    c.copy(javaType = fqn)
                } else c
            }
            t.copy(columns = cols2)
        }
        // 不再生成 mapper.yml 的类型定义文件
        // Freemarker 配置
        val fm = Configuration(Version("2.3.31"))
        fm.defaultEncoding = "UTF-8"
        var generatedCount = 0
        val errors = mutableListOf<String>()
        // 先为选中的枚举字段生成枚举类型
        if (enumSelMap.isNotEmpty()) {
            val pkgPath = cfgEff.packageName.replace('.', '/')
            val enumBaseDir = Path.of(base).resolve("src/main/java").resolve(pkgPath).resolve("enums")
            try { Files.createDirectories(enumBaseDir) } catch (_: Throwable) {}
            tablesAdj.forEach { t ->
                val selectedCols = enumSelMap[t.name] ?: emptyList()
                selectedCols.forEach { col ->
                    val enumName = "${t.entityName}${toCamelUpper(col)}Enum"
                    val overrideDirRaw = cfgEff.tableEnumOutputDirOverrides?.get(t.name)?.get(col)
                    val outDir = if (!overrideDirRaw.isNullOrBlank()) {
                        Path.of(overrideDirRaw)
                    } else enumBaseDir
                    try { Files.createDirectories(outDir) } catch (_: Throwable) {}
                    val target = outDir.resolve("$enumName.java")
                    try {
                        val tplName = (cfgEff.tableEnumTemplateOverrides?.get(t.name)?.get(col)) ?: cfgEff.enumTemplateName
                        val content: String = if (!tplName.isNullOrBlank() && tplName != "内置") {
                            val baseProj = project.basePath
                            val enumDir = if (baseProj != null) Path.of(baseProj).resolve("my-easy-code").resolve("templates").resolve("enums") else null
                            val f = enumDir?.resolve(tplName)
                            if (f != null && Files.exists(f)) {
                                val fm = freemarker.template.Configuration(freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS)
                                fm.setDefaultEncoding("UTF-8")
                                fm.setTemplateExceptionHandler(freemarker.template.TemplateExceptionHandler.RETHROW_HANDLER)
                                val data = mapOf(
                                    "enumPackage" to (cfgEff.packageName + ".enums"),
                                    "enumName" to enumName,
                                    "tableName" to t.name,
                                    "columnName" to col
                                )
                                val template = Template(tplName, StringReader(Files.readString(f)), fm)
                                val sw = java.io.StringWriter()
                                template.process(data, sw)
                                sw.toString()
                            } else {
                                "package ${cfgEff.packageName}.enums;\n\npublic enum $enumName {\n\n}\n"
                            }
                        } else {
                            "package ${cfgEff.packageName}.enums;\n\npublic enum $enumName {\n\n}\n"
                        }
                        Files.write(target, content.toByteArray(StandardCharsets.UTF_8))
                        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(enumBaseDir.toFile())
                        vDir?.refresh(false, true)
                    } catch (_: Throwable) {}
                }
            }
        }
        tablesAdj.forEach { t ->
            cfgEff.templates.forEach { tmpl ->
                if (!cfgEff.generateMapper && tmpl.name.equals("mapper", true)) return@forEach
                if (!cfgEff.generateMapperXml && tmpl.name.equals("mapperXml", true)) return@forEach
                val bothEx = cfg.tableBothExclude?.get(t.name) ?: emptyList()
                val dtoBase = cfg.dtoExclude ?: emptyList()
                val voBase = cfg.voExclude ?: emptyList()
                val unionBase = if (cfg.excludeApplyBoth) (dtoBase + voBase).distinct() else null
                // 仅当该模板参与“排除字段”时，合并各级排除集合
                val enabledTpls = cfg.templateExcludeTemplates ?: emptyList()
                val exclude = if (enabledTpls.any { it.equals(tmpl.name, true) }) {
                    (unionBase ?: emptyList()) +
                            bothEx +
                            (cfg.tableDtoExclude?.get(t.name) ?: emptyList()) +
                            (cfg.tableVoExclude?.get(t.name) ?: emptyList())
                } else emptyList()
                val imps = t.columns.mapNotNull {
                    val jt = it.javaType
                    val hasDot = jt.contains('.')
                    if (hasDot && !jt.startsWith("java.lang") && !jt.startsWith("kotlin.")) jt else null
                }.distinct()
                // 模板内容来源：优先 file 路径，其次内联 content
                val content = if (!tmpl.file.isNullOrBlank()) {
                    try {
                        val candidates = mutableListOf<Path>()
                        val tf = Path.of(tmpl.file)
                        if (tf.isAbsolute) {
                            candidates.add(tf)
                        } else {
                            candidates.add(templateBase.resolve(tmpl.file))
                            val base = project.basePath
                            if (base != null) {
                                candidates.add(Path.of(base).resolve("src/main/resources").resolve(tmpl.file))
                                candidates.add(Path.of(base).resolve("my-easy-code").resolve("templates").resolve(Path.of(tmpl.file).fileName.toString()))
                                candidates.add(Path.of(base).resolve(tmpl.file))
                            }
                        }
                        val filePath = candidates.firstOrNull { Files.exists(it) } ?: templateBase.resolve(tmpl.file)
                        Files.readString(filePath)
                    } catch (e: Throwable) {
                        errors += "读取模板失败: ${tmpl.name}(${t.name}) - ${e.message ?: e.toString()}"
                        ""
                    }
                } else {
                    tmpl.content ?: ""
                }
                // 渲染模板
                val template = Template(tmpl.name, StringReader(content), fm)
                val defaultPath = expandPath(tmpl.outputPath, base, cfgEff.packageName, t)
                val defDir = Path.of(defaultPath)
                val overrideRaw = cfgEff.templateDirOverrides?.get(tmpl.name)
                val finalDir = if (!overrideRaw.isNullOrBlank()) {
                    val pkgPath = cfgEff.packageName.replace('.', '/')
                    var p = overrideRaw
                    p = p.replace("\${baseDir}", base)
                    p = p.replace("\${projectBase}", base)
                    p = p.replace("\${packagePath}", pkgPath)
                    p = p.replace("\${packageName}", cfgEff.packageName)
                    p = p.replace("\${entityName}", t.entityName)
                    p = p.replace("\${tableName}", t.name)
                    val ov = Path.of(p)
                    val last = ov.fileName?.toString() ?: ""
                    if (last.contains('.')) ov.parent ?: ov else ov
                } else defDir
                fun pkgFromDir(dir: Path): String {
                    val baseJava = Path.of(base).resolve("src/main/java")
                    return try {
                        val rel = baseJava.relativize(dir).toString().replace('\\', '/')
                        rel.trim('/').replace('/', '.')
                    } catch (_: Throwable) {
                        cfgEff.packageName
                    }
                }
                val filePackage = pkgFromDir(finalDir)
                val nameOverride = cfgEff.templateFileNameOverrides?.get(tmpl.name)
                fun defaultFileNameFor(tmplName: String, fileType: String, entity: String): String {
                    val base = if (tmplName.equals("mapperXml", true)) "mapper" else tmplName
                    return entity + base
                }
                fun expandPattern(name: String, entity: String, table: String): String {
                    var s = name
                    s = s.replace("\${entityName}", entity)
                    s = s.replace("\${tableName}", table)
                    return s
                }
                val chosenRaw = if (!nameOverride.isNullOrBlank()) nameOverride!! else defaultFileNameFor(tmpl.name, tmpl.fileType, t.entityName)
                val chosenName = expandPattern(chosenRaw, t.entityName, t.name)
                val finalFile = if (chosenName.contains('.')) chosenName else chosenName + "." + when (tmpl.fileType.lowercase()) {
                    "java" -> "java"
                    "xml" -> "xml"
                    "kotlin", "kt" -> "kt"
                    "groovy", "gvy" -> "groovy"
                    else -> tmpl.fileType.lowercase()
                }
                fun effectivePackageFor(name: String): String {
                    val tmpl2 = cfgEff.templates.firstOrNull { it.name.equals(name, true) }
                    if (tmpl2 != null) {
                        val defPath2 = expandPath(tmpl2.outputPath, base, cfgEff.packageName, t)
                        val defDir2 = Path.of(defPath2).parent
                        val ovRaw2 = cfgEff.templateDirOverrides?.get(tmpl2.name)
                        val dir2 = if (!ovRaw2.isNullOrBlank()) {
                            val pkgPath = cfgEff.packageName.replace('.', '/')
                            var p2 = ovRaw2
                            p2 = p2.replace("\${baseDir}", base)
                            p2 = p2.replace("\${projectBase}", base)
                            p2 = p2.replace("\${packagePath}", pkgPath)
                            p2 = p2.replace("\${packageName}", cfgEff.packageName)
                            p2 = p2.replace("\${entityName}", t.entityName)
                            p2 = p2.replace("\${tableName}", t.name)
                            val ov2 = Path.of(p2)
                            val last2 = ov2.fileName?.toString() ?: ""
                            if (last2.contains('.')) ov2.parent ?: ov2 else ov2
                        } else defDir2
                        return pkgFromDir(dir2)
                    }
                    return cfgEff.packageName
                }
                val dtoPackage = effectivePackageFor("dto")
                val voPackage = effectivePackageFor("vo")
                val entityPackage = effectivePackageFor("entity")
                val servicePackage = effectivePackageFor("service")
                val mapperPackage = effectivePackageFor("mapper")
                val controllerPackage = effectivePackageFor("controller")
                val convertPackage = cfgEff.packageName + ".convert"
                val data = mapOf(
                    "packageName" to cfgEff.packageName,
                    "filePackage" to filePackage,
                    "table" to t,
                    "entityName" to t.entityName,
                    "stripPrefix" to (cfgEff.stripTablePrefix ?: ""),
                    "author" to (cfgEff.author ?: System.getProperty("user.name")),
                    "date" to java.time.LocalDate.now().toString(),
                    "exclude" to exclude,
                    "dtoExclude" to (cfgEff.dtoExclude ?: emptyList()),
                    "voExclude" to (cfgEff.voExclude ?: emptyList()),
                    "useLombok" to (cfgEff.useLombok),
                    "dtoImports" to imps,
                    "entityImports" to imps,
                    "voImports" to imps,
                    "dtoPackage" to dtoPackage,
                    "voPackage" to voPackage,
                    "entityPackage" to entityPackage,
                    "servicePackage" to servicePackage,
                    "mapperPackage" to mapperPackage,
                    "controllerPackage" to controllerPackage,
                    "convertPackage" to convertPackage
                )
                // 写文件 + 刷新 VFS + PSI 格式化
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        Files.createDirectories(finalDir)
                        val sb = java.io.StringWriter()
                        template.process(data, sb)
                        val target = finalDir.resolve(finalFile)
                        Files.write(target, sb.toString().toByteArray(StandardCharsets.UTF_8))
                        generatedCount++
                        val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(finalDir.toFile())
                        vDir?.refresh(false, true)
                        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.toFile())
                        if (vFile != null) {
                            val psi = PsiManager.getInstance(project).findFile(vFile)
                            if (psi != null) {
                                val psiMgr = PsiDocumentManager.getInstance(project)
                                val doc = psiMgr.getDocument(psi)
                                if (doc != null) psiMgr.commitDocument(doc)
                                WriteCommandAction.runWriteCommandAction(project) {
                                    CodeStyleManager.getInstance(project).reformat(psi)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        errors += "生成失败: ${tmpl.name}(${t.name}) - ${e.message ?: e.toString()}"
                    }
                }
            }
        }
        if (errors.isNotEmpty()) {
            val basePath = project.basePath ?: base
            if (basePath.isNotBlank()) {
                try {
                    val root = Path.of(basePath).resolve("my-easy-code")
                    Files.createDirectories(root)
                    val logFile = root.resolve("generate.log")
                    val stamp = java.time.LocalDateTime.now().toString()
                    val content = buildString {
                        append("time=").append(stamp).append('\n')
                        errors.forEach { append(it).append('\n') }
                    }
                    Files.writeString(logFile, content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
                } catch (_: Throwable) {}
            }
            try {
                val msg = errors.joinToString("\n")
                com.intellij.openapi.ui.Messages.showErrorDialog(project, msg, "生成错误详情")
            } catch (_: Throwable) {}
        } else {
            try {
                com.intellij.openapi.ui.Messages.showInfoMessage(project, "已生成文件数: $generatedCount", "生成完成")
            } catch (_: Throwable) {}
        }
    }

    /**
     * 展开输出路径中的占位符。
     */
    private fun expandPath(pattern: String, base: String, pkg: String, t: TableMeta): String {
        val pkgPath = pkg.replace('.', '/')
        val entityLower = t.entityName.replaceFirstChar { it.lowercaseChar() }
        var p = pattern
        p = p.replace("\${baseDir}", base)
        p = p.replace("\${projectBase}", base)
        p = p.replace("\${packagePath}", pkgPath)
        p = p.replace("\${packageName}", pkg)
        p = p.replace("\${entityName}", t.entityName)
        p = p.replace("\${entityNameLower}", entityLower)
        p = p.replace("\${tableName}", t.name)
        p = p.replace("\${tableNameLower}", t.name.lowercase())
        p = p.replace("\${date}", java.time.LocalDate.now().toString())
        return p
    }

    private fun toCamelUpper(name: String): String {
        val parts = name.lowercase().split('_')
        val b = StringBuilder()
        parts.forEach {
            if (it.isNotEmpty()) b.append(it[0].uppercaseChar()).append(it.substring(1))
        }
        return b.toString()
    }

    private fun toCamelLower(name: String): String {
        val up = toCamelUpper(name)
        return up.replaceFirstChar { it.lowercaseChar() }
    }
}
