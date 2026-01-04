package com.my.coder.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBTextField
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import com.my.coder.config.TemplateItem
import javax.swing.event.ListSelectionListener
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.application.ApplicationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 插件设置页：维护包名/目录/模板方案与开关（是否生成 Mapper/MapperXml），
 * 并支持将模板方案持久化到项目级状态。
 */
class GeneratorSettingsConfigurable : Configurable {
    private val packageField = JBTextField()
    private val baseDirField = TextFieldWithBrowseButton()
    private val yamlField = TextFieldWithBrowseButton()
    private val templateRootField = TextFieldWithBrowseButton()
    private val dtoExcludeField = JBTextField()
    private val voExcludeField = JBTextField()
    private val genMapperCb = javax.swing.JCheckBox("生成 Mapper", true)
    private val genMapperXmlCb = javax.swing.JCheckBox("生成 MapperXml", true)
    private val schemeListModel = DefaultListModel<String>()
    private val schemeList = JBList(schemeListModel)
    private val templateListModel = DefaultListModel<String>()
    private val templateList = JBList(templateListModel)
    private val schemeNameField = JBTextField()
    private val tmplNameField = JBTextField()
    private val tmplEngineField = JBTextField("freemarker")
    private val tmplFileTypeField = JBTextField("java")
    private val tmplFileField = TextFieldWithBrowseButton()
    private val tmplOutputField = JBTextField()
    private val tmplContentField = JBTextField()
    private val addSchemeBtn = JButton("Add Scheme")
    private val removeSchemeBtn = JButton("Remove Scheme")
    private val setActiveBtn = JButton("Set Active")
    private val addTemplateBtn = JButton("Add Template")
    private val removeTemplateBtn = JButton("Remove Template")
    private val importSamplesBtn = JButton("Import Sample Templates")
    private val importDirField = TextFieldWithBrowseButton()
    private val importFromDirBtn = JButton("Import From Directory")
    private var panel: JPanel? = null
    private val schemeTemplates = mutableMapOf<String, MutableList<TemplateItem>>()

    override fun getDisplayName(): String = "MyBatis Generator"

    override fun createComponent(): JComponent {
        val p = JPanel()
        p.layout = BoxLayout(p, BoxLayout.Y_AXIS)
        p.add(JLabel("Package Name"))
        p.add(packageField)
        p.add(JLabel("Base Dir"))
        p.add(baseDirField)
        
        p.add(JLabel("YAML Path"))
        p.add(yamlField)
        p.add(JLabel("Template Root"))
        p.add(templateRootField)
        p.add(genMapperCb)
        p.add(genMapperXmlCb)
        p.add(JLabel("如果 YAML 未配置 database，将使用 IDEA Database 的数据源"))
        p.add(JLabel("输出路径支持占位符：${'$'}{baseDir}、${'$'}{projectBase}、${'$'}{packagePath}、${'$'}{packageName}、${'$'}{entityName}、${'$'}{entityNameLower}、${'$'}{tableName}、${'$'}{tableNameLower}、${'$'}{date}"))
        p.add(JLabel("DTO Exclude (comma-separated)"))
        p.add(dtoExcludeField)
        p.add(JLabel("VO Exclude (comma-separated)"))
        p.add(voExcludeField)
        p.add(JLabel("Template Schemes"))
        p.add(schemeList)
        p.add(JLabel("Scheme Name"))
        p.add(schemeNameField)
        p.add(addSchemeBtn)
        p.add(removeSchemeBtn)
        p.add(setActiveBtn)
        p.add(JLabel("Templates in Selected Scheme"))
        p.add(templateList)
        p.add(JLabel("Template Name"))
        p.add(tmplNameField)
        p.add(JLabel("Engine"))
        p.add(tmplEngineField)
        p.add(JLabel("File Type (e.g. java, xml, kt)"))
        p.add(tmplFileTypeField)
        p.add(JLabel("Template File"))
        p.add(tmplFileField)
        p.add(JLabel("Output Path"))
        p.add(tmplOutputField)
        p.add(JLabel("Inline Content (optional)"))
        p.add(tmplContentField)
        p.add(addTemplateBtn)
        p.add(removeTemplateBtn)
        p.add(importSamplesBtn)
        p.add(JLabel("Import Directory"))
        p.add(importDirField)
        p.add(importFromDirBtn)

        addSchemeBtn.addActionListener {
            val name = schemeNameField.text.trim()
            if (name.isNotEmpty() && (0 until schemeListModel.size()).none { schemeListModel.get(it) == name }) {
                schemeListModel.addElement(name)
                schemeTemplates.putIfAbsent(name, mutableListOf())
            }
        }
        removeSchemeBtn.addActionListener {
            val idx = schemeList.selectedIndex
            if (idx >= 0) {
                val name = schemeListModel.get(idx)
                schemeListModel.remove(idx)
                schemeTemplates.remove(name)
            }
            templateListModel.clear()
        }
        setActiveBtn.addActionListener {
            // no-op in UI, applied in apply()
        }
        addTemplateBtn.addActionListener {
            val name = tmplNameField.text.trim()
            val engine = tmplEngineField.text.trim()
            val ftype = tmplFileTypeField.text.trim().ifBlank { "java" }
            val file = tmplFileField.text.trim()
            val out = tmplOutputField.text.trim()
            val content = tmplContentField.text
            if (name.isNotEmpty() && out.isNotEmpty()) {
                val schemeIdx = schemeList.selectedIndex
                if (schemeIdx >= 0) {
                    val schemeName = schemeListModel.get(schemeIdx)
                    val list = schemeTemplates.getOrPut(schemeName) { mutableListOf() }
                    val item = TemplateItem(name, engine.ifBlank { "freemarker" }, content.ifBlank { null }, file.ifBlank { null }, ensureExtension(out, ftype), ftype)
                    list.add(item)
                    templateListModel.addElement(renderTemplateItem(item))
                }
            }
        }
        removeTemplateBtn.addActionListener {
            val idx = templateList.selectedIndex
            if (idx >= 0) {
                val schemeIdx = schemeList.selectedIndex
                if (schemeIdx >= 0) {
                    val schemeName = schemeListModel.get(schemeIdx)
                    val list = schemeTemplates[schemeName]
                    if (list != null && idx < list.size) list.removeAt(idx)
                }
                templateListModel.remove(idx)
            }
        }
        importSamplesBtn.addActionListener {
            val schemeIdx = schemeList.selectedIndex
            if (schemeIdx >= 0) {
                val schemeName = schemeListModel.get(schemeIdx)
                val list = schemeTemplates.getOrPut(schemeName) { mutableListOf() }
                val samples = listOf(
                    TemplateItem("entity", "freemarker", null, "templates/entity.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/entity/${'$'}{entityName}.java"),
                    TemplateItem("mapper", "freemarker", null, "templates/mapper.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/${'$'}{entityName}Mapper.java"),
                    TemplateItem("mapperXml", "freemarker", null, "templates/mapperXml.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/xml/${'$'}{entityName}Mapper.xml"),
                    TemplateItem("service", "freemarker", null, "templates/service.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/${'$'}{entityName}Service.java"),
                    TemplateItem("serviceImpl", "freemarker", null, "templates/serviceImpl.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/impl/${'$'}{entityName}ServiceImpl.java"),
                    TemplateItem("controller", "freemarker", null, "templates/controller.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/controller/${'$'}{entityName}Controller.java"),
                    TemplateItem("dto", "freemarker", null, "templates/dto.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/dto/${'$'}{entityName}DTO.java"),
                    TemplateItem("vo", "freemarker", null, "templates/vo.ftl", "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/vo/${'$'}{entityName}VO.java")
                )
                samples.forEach { list.add(it); templateListModel.addElement(renderTemplateItem(it)) }
                val rootText = templateRootField.text.trim()
                val root = if (rootText.isBlank()) {
                    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    val vf = FileChooser.chooseFile(descriptor, null, null)
                    if (vf == null) return@addActionListener
                    templateRootField.text = vf.path
                    Paths.get(vf.path)
                } else Paths.get(rootText)
                ApplicationManager.getApplication().runWriteAction {
                    val dir = root.resolve("templates")
                    Files.createDirectories(dir)
                    fun copy(name: String) {
                        val inStream = javaClass.classLoader.getResourceAsStream("templates/$name.ftl") ?: return
                        val target = dir.resolve("$name.ftl")
                        Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                    copy("entity"); copy("mapper"); copy("mapperXml"); copy("service"); copy("serviceImpl"); copy("controller"); copy("dto"); copy("vo")
                }
            }
        }
        importFromDirBtn.addActionListener {
            val schemeIdx = schemeList.selectedIndex
            if (schemeIdx < 0) {
                Messages.showInfoMessage("请先选择一个模板方案", "Import Templates")
                return@addActionListener
            }
            val schemeName = schemeListModel.get(schemeIdx)
            val list = schemeTemplates.getOrPut(schemeName) { mutableListOf() }
            val dirText = importDirField.text.trim()
            if (dirText.isEmpty()) {
                Messages.showInfoMessage("请选择需要导入的目录", "Import Templates")
                return@addActionListener
            }
            val root = Paths.get(dirText)
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                Messages.showErrorDialog("目录不存在或不可用: $dirText", "Import Templates")
                return@addActionListener
            }
            if (templateRootField.text.isBlank()) {
                templateRootField.text = dirText
            }
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
                    .forEach { f ->
                        val rel = try { root.relativize(f).toString() } catch (e: Exception) { f.toString() }
                        val name = guessName(f.fileName.toString())
                        val out = suggestOutput(name, defaultTypeFor(name))
                        val item = TemplateItem(name, "freemarker", null, rel, out, defaultTypeFor(name))
                        list.add(item)
                        templateListModel.addElement(renderTemplateItem(item))
                    }
            }
        }
        schemeList.addListSelectionListener(ListSelectionListener {
            val idx = schemeList.selectedIndex
            templateListModel.clear()
            if (idx >= 0) {
                val name = schemeListModel.get(idx)
                val list = schemeTemplates[name] ?: mutableListOf()
                list.forEach { templateListModel.addElement(renderTemplateItem(it)) }
            }
        })
        panel = p
        return p
    }

    override fun isModified(): Boolean {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
        val s = project.service<GeneratorSettings>().state
        return (packageField.text != (s.packageName ?: "")) ||
                (baseDirField.text != (s.baseDir ?: "")) ||
                
                (yamlField.text != (s.yamlPath ?: "")) ||
                (templateRootField.text != (s.templateRoot ?: "")) ||
                (dtoExcludeField.text != ((s.dtoExclude ?: mutableListOf()).joinToString(","))) ||
                (voExcludeField.text != ((s.voExclude ?: mutableListOf()).joinToString(","))) ||
                (genMapperCb.isSelected != s.generateMapper) ||
                (genMapperXmlCb.isSelected != s.generateMapperXml) ||
                isSchemeModified(s)
    }

    private fun isSchemeModified(s: GeneratorSettings.State): Boolean {
        val namesInState = (s.schemes ?: mutableListOf()).map { it.name }
        val namesInUi = (0 until schemeListModel.size()).map { schemeListModel.get(it) }
        if (namesInState != namesInUi) return true
        val activeName = s.activeScheme ?: ""
        val uiActive = schemeList.selectedIndex.takeIf { it >= 0 }?.let { schemeListModel.get(it) } ?: namesInUi.firstOrNull()
        if (activeName != (uiActive ?: "")) return true
        val stateTemplatesCount = (s.schemes ?: mutableListOf()).sumOf { it.templates.size }
        val uiTemplatesCount = schemeTemplates.values.sumOf { it.size }
        if (stateTemplatesCount != uiTemplatesCount) return true
        return false
    }

    override fun apply() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
        val service = project.service<GeneratorSettings>()
        val s = service.state
        s.packageName = packageField.text.ifBlank { null }
        s.baseDir = baseDirField.text.ifBlank { null }
        
        s.yamlPath = yamlField.text.ifBlank { null }
        s.templateRoot = templateRootField.text.ifBlank { null }
        s.dtoExclude = dtoExcludeField.text.split(',').mapNotNull { val v = it.trim(); if (v.isEmpty()) null else v }.toMutableList()
        s.voExclude = voExcludeField.text.split(',').mapNotNull { val v = it.trim(); if (v.isEmpty()) null else v }.toMutableList()
        s.generateMapper = genMapperCb.isSelected
        s.generateMapperXml = genMapperXmlCb.isSelected
        val names = (0 until schemeListModel.size()).map { schemeListModel.get(it) }
        val list = mutableListOf<GeneratorSettings.TemplateScheme>()
        names.forEach { n ->
            val sch = GeneratorSettings.TemplateScheme()
            sch.name = n
            sch.templates = schemeTemplates[n] ?: mutableListOf()
            list.add(sch)
        }
        s.schemes = list
        val idx = schemeList.selectedIndex
        s.activeScheme = if (idx >= 0 && idx < names.size) names[idx] else list.firstOrNull()?.name
    }

    override fun reset() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
        val s = project.service<GeneratorSettings>().state
        packageField.text = s.packageName ?: ""
        baseDirField.text = s.baseDir ?: ""
        
        yamlField.text = s.yamlPath ?: ""
        templateRootField.text = s.templateRoot ?: ""
        dtoExcludeField.text = (s.dtoExclude ?: mutableListOf()).joinToString(",")
        voExcludeField.text = (s.voExclude ?: mutableListOf()).joinToString(",")
        genMapperCb.isSelected = s.generateMapper
        genMapperXmlCb.isSelected = s.generateMapperXml
        schemeListModel.clear()
        schemeTemplates.clear()
        (s.schemes ?: mutableListOf()).forEach { sch ->
            schemeListModel.addElement(sch.name)
            schemeTemplates[sch.name] = sch.templates
        }
        val activeIdx = (0 until schemeListModel.size()).indexOfFirst { schemeListModel.get(it) == s.activeScheme }
        if (activeIdx >= 0) schemeList.selectedIndex = activeIdx
        templateListModel.clear()
        if (activeIdx >= 0) {
            val name = schemeListModel.get(activeIdx)
            schemeTemplates[name]?.forEach { templateListModel.addElement(renderTemplateItem(it)) }
        }
    }

    private fun renderTemplateItem(it: TemplateItem): String = buildString {
        append(it.name).append(" | ").append(it.engine)
        append(" | file=").append(it.file ?: "inline")
        append(" | out=").append(it.outputPath)
        append(" | type=").append(it.fileType)
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
            l.contains("convert") || l.contains("mapstruct") -> "convert"
            l.contains("dto") -> "dto"
            l.contains("vo") -> "vo"
            else -> n
        }
    }

    private fun suggestOutput(name: String, fileType: String): String {
        val ext = extensionFor(fileType)
        return when (name) {
            "entity" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/entity/${'$'}{entityName}.$ext"
            "mapper" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/${'$'}{entityName}Mapper.$ext"
            "mapperXml" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/xml/${'$'}{entityName}Mapper.${extensionFor("xml")}"
            "service" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/${'$'}{entityName}Service.$ext"
            "serviceImpl" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/impl/${'$'}{entityName}ServiceImpl.$ext"
            "controller" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/controller/${'$'}{entityName}Controller.$ext"
            "dto" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/dto/${'$'}{entityName}DTO.$ext"
            "vo" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/vo/${'$'}{entityName}VO.$ext"
            else -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/${'$'}{entityName}.$ext"
        }
    }
    private fun defaultTypeFor(name: String): String = if (name.equals("mapperXml", true)) "xml" else "java"
    private fun extensionFor(fileType: String): String = when (fileType.lowercase()) {
        "java" -> "java"
        "xml" -> "xml"
        "kotlin", "kt" -> "kt"
        "groovy", "gvy" -> "groovy"
        else -> fileType.lowercase()
    }
    private fun ensureExtension(path: String, fileType: String): String {
        val fn = java.nio.file.Path.of(path).fileName.toString()
        return if (fn.contains('.')) path else path + "." + extensionFor(fileType)
    }

    override fun disposeUIResources() {
        panel = null
    }
}
