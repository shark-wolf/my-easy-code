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
        // template adding UI removed

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
        // addTemplate disabled
        // removeTemplate disabled
        // importSamples disabled
        // importFromDir disabled
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
        return when (name) {
            "entity" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/entity/"
            "mapper" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/"
            "mapperXml" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/mapper/xml/"
            "service" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/"
            "serviceImpl" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/service/impl/"
            "controller" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/controller/"
            "dto" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/dto/"
            "vo" -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/vo/"
            else -> "${'$'}{baseDir}/src/main/java/${'$'}{packagePath}/"
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
