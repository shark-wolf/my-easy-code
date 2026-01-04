package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.database.util.DasUtil
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.psi.DbTable
import com.my.coder.config.ColumnMeta
import com.my.coder.config.TableMeta
import com.my.coder.settings.GeneratorSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.ide.DataManager
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.Version
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object TemplatePreview {
    fun open(project: Project, file: VirtualFile) {
        val st = project.service<GeneratorSettings>().state
        val pkg = st.packageName ?: "com.example"
        val baseDir = st.baseDir ?: project.basePath ?: ""
        val name = guessName(file.name)
        val isEnumTmpl = try {
            val p = file.path.replace('\\','/').lowercase()
            p.contains("/my-easy-code/templates/enums/") || name.lowercase() == "enum" || file.name.lowercase().contains("enum")
        } catch (_: Throwable) { false }
        val defOut = suggestOutput(name)
        val table = resolveSelectedTable(project) ?: resolveTable(project) ?: sampleTable()
        val defPath = expand(defOut, baseDir, pkg, table)
        val defDir = Path.of(defPath)
        val defFile0 = "file"
        val overrideRaw = st.templateOutputs?.get(name)
        val finalDir = if (!overrideRaw.isNullOrBlank()) {
            val p = expand(overrideRaw!!, baseDir, pkg, table)
            val ov = Path.of(p)
            val last = ov.fileName?.toString() ?: ""
            if (last.contains('.')) ov.parent ?: ov else ov
        } else defDir
        val filePackage = packageFromDir(finalDir, baseDir, pkg)
        val nameOverride = st.templateFileNames?.get(name)
        fun defaultFileNameFor(tmplName: String, entity: String): String {
            val ext = if (tmplName.equals("mapperXml", true)) "xml" else "java"
            val raw = if (tmplName.equals("mapperXml", true)) "mapper" else tmplName
            val base = raw.replaceFirstChar { it.uppercaseChar() }
            return entity + base + "." + ext
        }
        fun expandPattern(name: String, entity: String, tableName: String): String {
            var s = name
            s = s.replace("\${entityName}", entity)
            s = s.replace("\${tableName}", tableName)
            return s
        }
        fun classNameFor(tmplName: String, entity: String, tableName: String): String {
            val ov = st.templateFileNames?.get(tmplName)
            val raw = if (!ov.isNullOrBlank()) ov!! else defaultFileNameFor(tmplName, entity)
            val chosen = expandPattern(raw, entity, tableName)
            return if (chosen.contains('.')) chosen.substringBeforeLast('.') else chosen
        }
        val chosenRaw = if (!nameOverride.isNullOrBlank()) nameOverride!! else defaultFileNameFor(name, table.entityName)
        val defFile = expandPattern(chosenRaw, table.entityName, table.name)
        val fm = Configuration(Version("2.3.31"))
        fm.defaultEncoding = "UTF-8"
        fm.setNumberFormat("computer")
        val doc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)
        val tplText = doc?.text ?: VfsUtil.loadText(file)
        val template = Template(name, StringReader(tplText), fm)
        val out = java.io.StringWriter()
        if (isEnumTmpl) {
            val priorities = listOf("status","state","type","category","flag","enabled","is_deleted","deleted")
            val names = table.columns.map { it.name }.map { it.lowercase() }
            val pick = priorities.firstOrNull { names.contains(it) }
            val colMeta = if (pick != null) table.columns.firstOrNull { it.name.equals(pick, true) } else table.columns.firstOrNull()
            val colName = colMeta?.name ?: "status"
            val enumName = "${table.entityName}${toCamelUpper(colName)}Enum"
            fun sanitizeEnumName(s: String): String {
                val base = s.trim().replace('[', '_').replace(']', '_')
                    .replace('-', '_').replace('—','_').replace('－','_')
                    .replace(' ', '_').replace('.', '_').replace('/', '_')
                val only = base.map { ch -> if (ch.isLetterOrDigit() || ch == '_') ch else '_' }.joinToString("")
                val up = only.uppercase()
                return if (up.isNotBlank()) up else "UNDEFINED"
            }
            fun parseEnumItems(comment: String?): List<Map<String, String>> {
                if (comment.isNullOrBlank()) return emptyList()
                val colonIdx = listOf('：', ':').map { ch -> comment.indexOf(ch) }.firstOrNull { it >= 0 } ?: -1
                val src = if (colonIdx >= 0) comment.substring(colonIdx + 1) else comment
                val text = src.replace('；', ',').replace('，', ',').replace(';', ',')
                val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val items = mutableListOf<Map<String, String>>()
                parts.forEach { p ->
                    val seg = p.split('-', '—', '－', ':').map { it.trim() }.filter { it.isNotEmpty() }
                    if (seg.size >= 2) {
                        val code = seg[0]
                        val label = seg.subList(1, seg.size).joinToString("-")
                        items += mapOf(
                            "code" to code,
                            "label" to label,
                            "name" to sanitizeEnumName(code)
                        )
                    }
                }
                return items
            }
            val enumItems = parseEnumItems(try { colMeta?.comment } catch (_: Throwable) { null })
            val data = mapOf(
                "packageName" to pkg,
                "enumPackage" to (pkg + ".enums"),
                "filePackage" to (pkg + ".enums"),
                "enumName" to enumName,
                "className" to enumName,
                "tableName" to table.name,
                "columnName" to colName,
                "columnComment" to (try { colMeta?.comment } catch (_: Throwable) { null } ?: ""),
                "enumItems" to enumItems
            )
            template.process(data, out)
            val dlg = com.my.coder.ui.TemplatePreviewDialog(project, enumName + ".java", out.toString())
            dlg.show()
        } else {
            val imps = table.columns.mapNotNull {
                val jt = it.javaType
                val hasDot = jt.contains('.')
                if (hasDot && !jt.startsWith("java.lang") && !jt.startsWith("kotlin.")) jt else null
            }.distinct()
            val className = defFile.substringBeforeLast('.', defFile)
            val dtoPkg = filePackage
            val voPkg = filePackage
            val entityPkg = filePackage
            val servicePkg = filePackage
            val mapperPkg = filePackage
            val controllerPkg = filePackage
            val convertPkg = filePackage
            val serialUid = run {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                md.update((pkg + "." + table.entityName + "." + name).toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                val b = md.digest()
                val bb = java.nio.ByteBuffer.wrap(java.util.Arrays.copyOfRange(b, 0, 8))
                val v = bb.long
                if (v < 0) -v else v
            }
            val data = mutableMapOf<String, Any>(
                "packageName" to pkg,
                "filePackage" to filePackage,
                "table" to table,
                "entityName" to table.entityName,
                "isEmptyImpl" to false,
                "className" to className,
                "dtoClassName" to classNameFor("dto", table.entityName, table.name),
                "voClassName" to classNameFor("vo", table.entityName, table.name),
                "entityClassName" to classNameFor("entity", table.entityName, table.name),
                "mapperClassName" to classNameFor("mapper", table.entityName, table.name),
                "serviceClassName" to classNameFor("service", table.entityName, table.name),
                "serviceImplClassName" to classNameFor("serviceImpl", table.entityName, table.name),
                "controllerClassName" to classNameFor("controller", table.entityName, table.name),
                "convertClassName" to classNameFor("convert", table.entityName, table.name),
                "mapperXmlClassName" to classNameFor("mapperXml", table.entityName, table.name),
                "stripPrefix" to (st.stripTablePrefix ?: ""),
                "author" to (st.author ?: System.getProperty("user.name")),
                "date" to java.time.LocalDate.now().toString(),
                "exclude" to emptyList<String>(),
                "dtoExclude" to (st.dtoExclude ?: emptyList()),
                "voExclude" to (st.voExclude ?: emptyList()),
                "useLombok" to (st.useLombok),
                "dtoImports" to imps,
                "entityImports" to imps,
                "voImports" to imps,
                "dtoPackage" to dtoPkg,
                "voPackage" to voPkg,
                "entityPackage" to entityPkg,
                "servicePackage" to servicePkg,
                "mapperPackage" to mapperPkg,
                "controllerPackage" to controllerPkg,
                "convertPackage" to convertPkg,
                "serviceImplPackage" to servicePkg,
                "mapperXmlPackage" to mapperPkg,
                "serialVersionUID" to serialUid
            )
            run {
                data[name + "Package"] = filePackage
                data[name + "ClassName"] = className
            }
            run {
                val tplRoot = Path.of(baseDir).resolve("my-easy-code").resolve("templates").resolve("general")
                val names = try {
                    if (Files.exists(tplRoot)) {
                        Files.list(tplRoot).use { s ->
                            s.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
                                .map { it.fileName.toString().substringBeforeLast('.') }
                                .toList()
                        }
                    } else emptyList()
                } catch (_: Throwable) { emptyList() }
                names.forEach { nm ->
                    val defOut = suggestOutput(nm)
                    val p0 = expand(defOut, baseDir, pkg, table)
                    val d0 = Path.of(p0)
                    val ovRaw = st.templateOutputs?.get(nm)
                    val finalD = if (!ovRaw.isNullOrBlank()) {
                        val ep = expand(ovRaw!!, baseDir, pkg, table)
                        val ov = Path.of(ep)
                        val last = ov.fileName?.toString() ?: ""
                        if (last.contains('.')) ov.parent ?: ov else ov
                    } else d0
                    val pkgFor = packageFromDir(finalD, baseDir, pkg)
                    data[nm + "Package"] = pkgFor
                    data[nm + "ClassName"] = classNameFor(nm, table.entityName, table.name)
                }
            }
            template.process(data, out)
            val dlg = com.my.coder.ui.TemplatePreviewDialog(project, defFile, out.toString())
            dlg.show()
        }
    }
    private fun guessName(fileName: String): String {
        val n = fileName.substringBeforeLast('.')
        val l = n.lowercase()
        return when {
            l.contains("entity") -> "entity"
            l.contains("mapperxml") || l == "xml" || l.contains("mapper.xml") -> "mapperXml"
            l.contains("mapper") -> "mapper"
            l.contains("serviceimpl") || l.contains("impl") -> "serviceImpl"
            l.contains("service") -> "service"
            l.contains("controller") -> "controller"
            l.contains("dto") -> "dto"
            l.contains("vo") -> "vo"
            else -> n
        }
    }
    private fun suggestOutput(name: String): String {
        return when (name) {
            "entity" -> "\${baseDir}/src/main/java/\${packagePath}/entity/"
            "mapper" -> "\${baseDir}/src/main/java/\${packagePath}/mapper/"
            "mapperXml" -> "\${baseDir}/src/main/java/\${packagePath}/mapper/xml/"
            "service" -> "\${baseDir}/src/main/java/\${packagePath}/service/"
            "serviceImpl" -> "\${baseDir}/src/main/java/\${packagePath}/service/impl/"
            "controller" -> "\${baseDir}/src/main/java/\${packagePath}/controller/"
            "dto" -> "\${baseDir}/src/main/java/\${packagePath}/dto/"
            "vo" -> "\${baseDir}/src/main/java/\${packagePath}/vo/"
            else -> "\${baseDir}/src/main/java/\${packagePath}/"
        }
    }
    private fun expand(pattern: String, baseDir: String, packageName: String, t: TableMeta): String {
        val pkgPath = packageName.replace('.', '/')
        var p = pattern
        p = p.replace("\${baseDir}", baseDir)
        p = p.replace("\${projectBase}", baseDir)
        p = p.replace("\${packagePath}", pkgPath)
        p = p.replace("\${packageName}", packageName)
        p = p.replace("\${entityName}", t.entityName)
        p = p.replace("\${tableName}", t.name)
        return p
    }
    private fun packageFromDir(dir: Path, baseDir: String, defaultPkg: String): String {
        val baseJava = Path.of(baseDir).resolve("src/main/java")
        return try {
            val rel = baseJava.relativize(dir).toString().replace('\\', '/')
            rel.trim('/').replace('/', '.')
        } catch (_: Throwable) {
            defaultPkg
        }
    }
    private fun resolveTable(project: Project): TableMeta? {
        val sources = DbPsiFacade.getInstance(project).dataSources
        sources.forEach { src ->
            for (t in DasUtil.getTables(src.delegate)) {
                val cols = mutableListOf<ColumnMeta>()
                for (c in DasUtil.getColumns(t)) {
                    val typeName = c.dataType.typeName
                    val size = c.dataType.size
                    val nullable = !c.isNotNull
                    val primary = DasUtil.isPrimary(c)
                    val javaType = com.my.coder.type.TypeMapper.toJavaType(typeName, size ?: 0, nullable)
                    cols += ColumnMeta(c.name, typeName, nullable, primary, javaType, toCamelLower(c.name), size, try { c.comment } catch (_: Throwable) { null })
                }
                return TableMeta(t.name, toCamelUpper(t.name), cols, try { t.comment } catch (_: Throwable) { null })
            }
        }
        return null
    }
    private fun resolveSelectedTable(project: Project): TableMeta? {
        val st = project.service<GeneratorSettings>().state
        val last = st.lastSelectedTables?.firstOrNull() ?: return null
        val facade = DbPsiFacade.getInstance(project)
        facade.dataSources.forEach { src ->
            for (t in DasUtil.getTables(src.delegate)) {
                if (t.name == last) {
                    val cols = mutableListOf<ColumnMeta>()
                    for (c in DasUtil.getColumns(t)) {
                        val typeName = c.dataType.typeName
                        val size = c.dataType.size
                        val nullable = !c.isNotNull
                        val primary = DasUtil.isPrimary(c)
                        val javaType = com.my.coder.type.TypeMapper.toJavaType(typeName, size ?: 0, nullable)
                        cols += ColumnMeta(c.name, typeName, nullable, primary, javaType, toCamelLower(c.name), size, try { c.comment } catch (_: Throwable) { null })
                    }
                    return TableMeta(t.name, toCamelUpper(t.name), cols, try { t.comment } catch (_: Throwable) { null })
                }
            }
        }
        return null
    }
    private fun sampleTable(): TableMeta {
        val base = "sample"
        return TableMeta(base, toCamelUpper(base), emptyList(), null)
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
