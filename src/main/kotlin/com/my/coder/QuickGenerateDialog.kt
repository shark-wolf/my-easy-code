package com.my.coder

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBList
import javax.swing.DefaultListModel
import javax.swing.JTextArea
import javax.swing.JCheckBox
import javax.swing.JScrollPane
import javax.swing.BoxLayout
import javax.swing.JSplitPane
import javax.swing.JButton
import javax.swing.BorderFactory
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import com.my.coder.config.GeneratorConfig
import com.my.coder.config.TemplateItem
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import java.awt.Font
import javax.swing.plaf.basic.BasicSplitPaneUI
import javax.swing.plaf.basic.BasicSplitPaneDivider
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import com.my.coder.settings.GeneratorSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ide.projectView.ProjectView

/**
 * 代码生成弹框：左侧为数据库表复选列表，右侧为配置区（包名/目录/模板选择/输出预览），
 * 支持按表选择在 DTO/VO 中排除的字段，并提供“一起生效”开关控制作用范围。
 */
class QuickGenerateDialog(private val project: Project, private val initialSelectedNames: List<String>) : DialogWrapper(project) {
    private val packageField = JBTextField()
    private val baseDirField = TextFieldWithBrowseButton()
    private val packageNameField = TextFieldWithBrowseButton()
    private val tablePrefixField = JBTextField()
    
    private val templateRootField = TextFieldWithBrowseButton()
    private val dtoExcludeField = JBTextField()
    private val voExcludeField = JBTextField()
    private val templateCheckBoxes = mutableListOf<JCheckBox>()
    private val templateOutputFields = mutableListOf<TextFieldWithBrowseButton>()
    private lateinit var templatePanel: JPanel
    private var allTemplates: List<TemplateItem> = emptyList()
    private val previewArea = JTextArea(12, 60)
    private val typeOverrideField = JTextArea(4, 40)
    private val columnTypeOverrideField = JTextArea(4, 40)
    private val importBtn = JButton("导入插件模板到项目根目录")
    private val tableCheckboxes = mutableListOf<JCheckBox>()
    private val applyBothCb = JCheckBox("排除字段在 DTO 和 VO 一起生效", false)
    private val tableTabs = javax.swing.JTabbedPane()
    private val tableDtoExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableVoExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableBothExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableEnumFieldsMap = mutableMapOf<String, MutableSet<String>>()
    
    
    
    private val useLombokCb = JCheckBox("使用 Lombok", true)
    
    init {
        title = "MyBatis Quick Generate"
        isModal = true
        baseDirField.text = project.basePath ?: ""
        val root = project.basePath?.let { Path.of(it).resolve("my-easy-code").resolve("templates").toString() } ?: ""
        templateRootField.text = root
        previewArea.isEditable = false
        
        init()
    }
    /**
     * 构建中心 UI：分为左右两个区域，并按主题美化分割条与模块标题。
     */
    override fun createCenterPanel(): JComponent {
        val settings = project.service<GeneratorSettings>()
        val st = settings.state
        val leftContainer = JPanel()
        leftContainer.layout = BoxLayout(leftContainer, BoxLayout.Y_AXIS)
        val left = JPanel()
        left.layout = BoxLayout(left, BoxLayout.Y_AXIS)
        left.alignmentX = 0f
        tableCheckboxes.clear()
        val preselect = initialSelectedNames.toSet()
        fetchAllTables().forEach { name ->
            val cb = JCheckBox(name, preselect.isEmpty() || preselect.contains(name))
            cb.addActionListener { refreshTableTabs() }
            tableCheckboxes.add(cb)
            left.add(cb)
        }
        left.border = BorderFactory.createTitledBorder("数据表")
        leftContainer.alignmentX = 0f
        val leftScroll = JScrollPane(left)
        leftScroll.border = BorderFactory.createEmptyBorder()
        leftScroll.alignmentX = 0f
        leftContainer.add(leftScroll)

        val right = JPanel()
        right.layout = GridBagLayout()
        val rc = GridBagConstraints()
        rc.insets = Insets(8,12,8,12)
        rc.gridx = 0; rc.gridy = 0; rc.fill = GridBagConstraints.HORIZONTAL; rc.weightx = 1.0
        val pkgRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
        packageNameField.textField.columns = 20
        packageNameField.preferredSize = java.awt.Dimension(JBUI.scale(300), JBUI.scale(28))
        val preLabel = JLabel("删除表名前缀")
        tablePrefixField.columns = 12
        tablePrefixField.preferredSize = java.awt.Dimension(JBUI.scale(200), JBUI.scale(28))
        pkgRow.add(JLabel("包名"))
        pkgRow.add(packageNameField)
        pkgRow.add(preLabel)
        pkgRow.add(tablePrefixField)
        right.add(pkgRow, rc)
        val pkgSaved = st.packageName
        if (!pkgSaved.isNullOrBlank()) packageNameField.text = pkgSaved
        val stripSaved = st.stripTablePrefix
        if (!stripSaved.isNullOrBlank()) tablePrefixField.text = stripSaved
        rc.gridy = 1
        right.add(JLabel("基准目录"), rc)
        rc.gridy = 2
        right.add(baseDirField, rc)
        val bdSaved = st.baseDir
        if (!bdSaved.isNullOrBlank()) baseDirField.text = bdSaved
        rc.gridy = 3
        right.add(JLabel("选择模板（多选）"), rc)
        templatePanel = JPanel()
        templatePanel.layout = BoxLayout(templatePanel, BoxLayout.Y_AXIS)
        refreshTemplateList()
        rc.gridy = 4; rc.weighty = 0.5; rc.fill = GridBagConstraints.BOTH
        val templatesScroll = JScrollPane(templatePanel)
        templatesScroll.border = BorderFactory.createTitledBorder("模板选择")
        right.add(templatesScroll, rc)
        rc.gridy = 5; rc.weighty = 0.5; rc.fill = GridBagConstraints.BOTH
        tableTabs.border = BorderFactory.createTitledBorder("排除字段和枚举字段")
        right.add(tableTabs, rc)
        rc.gridy = 6; rc.weighty = 0.0; rc.fill = GridBagConstraints.HORIZONTAL
        val bottomBar = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
        bottomBar.add(useLombokCb)
        bottomBar.add(applyBothCb)
        importBtn.text = "导入模板"
        importBtn.icon = com.intellij.icons.AllIcons.Actions.Download
        importBtn.toolTipText = "将插件内置模板复制到 my-easy-code/templates"
        bottomBar.add(importBtn)
        right.add(bottomBar, rc)
        val onSelChange = {
            refreshTableTabs()
        }
        applyBothCb.addActionListener { st.excludeApplyBoth = applyBothCb.isSelected; onSelChange.invoke() }
        useLombokCb.addActionListener { st.useLombok = useLombokCb.isSelected }
        tableCheckboxes.forEach { cb -> cb.addActionListener { onSelChange.invoke() } }
        refreshTableTabs()
        

        // 左右分栏与分割条美化
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftContainer, right)
        split.isContinuousLayout = true
        split.setDividerLocation(0.3)
        split.resizeWeight = 0.3
        split.dividerSize = JBUI.scale(2)
        split.border = BorderFactory.createEmptyBorder()
        split.setOneTouchExpandable(true)
        val ui = split.ui as? BasicSplitPaneUI
        val divider: BasicSplitPaneDivider? = ui?.divider
        divider?.background = JBColor(Color(0xEDEDED), Color(0x3C3F41))
        divider?.border = BorderFactory.createMatteBorder(0, 1, 0, 1, JBColor(Color(0xCFCFCF), Color(0x565656)))
        divider?.cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        val wrapper = JPanel(BorderLayout())
        wrapper.border = BorderFactory.createEmptyBorder(6,6,6,6)
        wrapper.add(split, BorderLayout.CENTER)
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val w = (screen.width * 0.6).toInt()
        val h = (screen.height * 0.6).toInt()
        wrapper.preferredSize = java.awt.Dimension(w, h)
        baseDirField.toolTipText = "生成代码的基准目录"
        packageNameField.toolTipText = "输入包名或选择包目录自动填充"
        tablePrefixField.toolTipText = "生成实体名时删除该前缀（如 t_ 或 tbl_）"
        templateRootField.toolTipText = "模板所在目录，支持项目/my-easy-code/templates"
        baseDirField.addBrowseFolderListener(
            "选择基准目录",
            null,
            project,
            com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        packageNameField.addBrowseFolderListener(
            "选择包目录",
            null,
            project,
            com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        val pkgDoc2 = packageNameField.textField.document
        pkgDoc2.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { updatePackageFromChooser() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { updatePackageFromChooser() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { updatePackageFromChooser() }
        })
        packageNameField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            private fun save() { st.packageName = packageNameField.text.trim() }
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { save() }
        })
        tablePrefixField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            private fun save() { st.stripTablePrefix = tablePrefixField.text.trim().ifBlank { null } }
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { save() }
        })
        useLombokCb.isSelected = st.useLombok
        applyBothCb.isSelected = st.excludeApplyBoth
        typeOverrideField.toolTipText = "按数据库类型覆盖 Java 类型，每行 type=JavaType"
        columnTypeOverrideField.toolTipText = "按列名覆盖 Java 类型，每行 column=JavaType"
        importBtn.addActionListener {
            val base = project.basePath ?: return@addActionListener
            val dest = Path.of(base).resolve("my-easy-code").resolve("templates")
            Files.createDirectories(dest)
            val names = listOf("entity","mapper","mapperXml","service","serviceImpl","controller","dto","vo")
            names.forEach { n ->
                val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
                if (inStream != null) {
                    val target = dest.resolve("$n.ftl")
                    Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            refreshTemplateList()
            val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dest.toFile())
            vDir?.refresh(false, true)
            ProjectView.getInstance(project).refresh()
        }
        return wrapper
    }
    private fun updatePackageFromChooser() {
        val raw = packageNameField.text.trim().replace('\\','/')
        if (raw.isEmpty()) return
        val marker = "/src/main/java/"
        val pkg = if (raw.contains(marker)) {
            raw.substringAfter(marker).trim('/').replace('/','.')
        } else if (raw.contains('/')) {
            raw.substringAfterLast("/src/main/java").trim('/').replace('/','.')
        } else raw
        packageNameField.text = pkg
    }
    /**
     * 汇总弹框中的用户输入为生成配置对象。
     */
    fun config(): GeneratorConfig {
        val pkgInput = packageNameField.text.trim()
        val pkg = if (pkgInput.isNotEmpty()) pkgInput else inferPackage()
        val baseDir = baseDirField.text.ifBlank { project.basePath }
        val author: String? = null
        allTemplates = scanTemplates(templateRoot())
        val selected = selectedTemplates()
        var templates = if (selected.isNotEmpty()) selected else allTemplates
        templates = templates.filterNot { it.name.equals("enum", true) }
        val dtoEx = emptyList<String>()
        val voEx = emptyList<String>()
        val typeOv = emptyMap<String, String>()
        val colOv = emptyMap<String, String>()
        val tableDtoEx = tableDtoExcludeMap.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        val tableVoEx = tableVoExcludeMap.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        val tableBothEx = tableBothExcludeMap.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        val enumSel = tableEnumFieldsMap.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        val settings = project.service<GeneratorSettings>()
        val dirOverrides = settings.state.templateOutputs?.toMap()
        return GeneratorConfig(
            com.my.coder.config.Database("", "", "", ""),
            pkg,
            baseDir,
            templates,
            com.my.coder.config.Tables(null, null),
            author,
            dtoEx,
            voEx,
            typeOv,
            colOv,
            useLombokCb.isSelected,
            generateMapper = true,
            generateMapperXml = true,
            tableDtoExclude = if (!applyBothCb.isSelected && tableDtoEx.isNotEmpty()) tableDtoEx else null,
            tableVoExclude = if (!applyBothCb.isSelected && tableVoEx.isNotEmpty()) tableVoEx else null,
            tableBothExclude = if (applyBothCb.isSelected && tableBothEx.isNotEmpty()) tableBothEx else null,
            excludeApplyBoth = applyBothCb.isSelected,
            applyMapperMapping = false,
            stripTablePrefix = tablePrefixField.text.trim().ifBlank { null },
            tableEnumFields = if (enumSel.isNotEmpty()) enumSel else null,
            templateDirOverrides = dirOverrides
        )
    }

    private fun fetchColumns(table: String): List<String> {
        val facade = com.intellij.database.psi.DbPsiFacade.getInstance(project)
        val cols = mutableListOf<String>()
        facade.dataSources.forEach { src ->
            for (t in com.intellij.database.util.DasUtil.getTables(src.delegate)) {
                if (t.name == table) {
                    for (c in com.intellij.database.util.DasUtil.getColumns(t)) cols += c.name
                }
            }
        }
        return cols.distinct().sorted()
    }
    fun templateRoot(): Path {
        val base = project.basePath ?: ""
        return Path.of(base).resolve("my-easy-code").resolve("templates")
    }
    /**
     * 扫描模板目录，识别 ftl 文件并推断模板名称与默认输出路径。
     */
    private fun scanTemplates(root: Path): List<TemplateItem> {
        if (!Files.exists(root)) return emptyList()
        val list = mutableListOf<TemplateItem>()
        Files.walk(root).use { s ->
            s.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
                .forEach { f ->
                    val name = guessName(f.fileName.toString())
                    val rel = try { root.relativize(f).toString() } catch (_: Exception) { f.toString() }
                    val out = suggestOutput(name)
                    list.add(TemplateItem(name, "freemarker", null, rel, out))
                }
        }
        return list
    }
    /**
     * 根据文件名推断模板逻辑名称。
     */
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
    /**
     * 根据模板名称返回默认输出路径模式（支持占位符）。
     */
    private fun suggestOutput(name: String): String {
        return when (name) {
            "entity" -> "\${baseDir}/src/main/java/\${packagePath}/entity/\${entityName}.java"
            "mapper" -> "\${baseDir}/src/main/java/\${packagePath}/mapper/\${entityName}Mapper.java"
            "mapperXml" -> "\${baseDir}/src/main/java/\${packagePath}/mapper/xml/\${entityName}Mapper.xml"
            "service" -> "\${baseDir}/src/main/java/\${packagePath}/service/\${entityName}Service.java"
            "serviceImpl" -> "\${baseDir}/src/main/java/\${packagePath}/service/impl/\${entityName}ServiceImpl.java"
            "controller" -> "\${baseDir}/src/main/java/\${packagePath}/controller/\${entityName}Controller.java"
            "dto" -> "\${baseDir}/src/main/java/\${packagePath}/dto/\${entityName}DTO.java"
            "vo" -> "\${baseDir}/src/main/java/\${packagePath}/vo/\${entityName}VO.java"
            else -> "\${baseDir}/src/main/java/\${packagePath}/\${entityName}.java"
        }
    }
    private fun inferPackage(): String {
        val base = project.basePath ?: return ""
        val p = Path.of(base).resolve("src/main/java")
        if (!Files.exists(p)) return ""
        return "com.example"
    }
    private fun refreshTemplateList() {
        allTemplates = scanTemplates(templateRoot()).let { list ->
            list.filterNot { it.name.equals("enum", true) }
        }
        templatePanel.removeAll()
        templateCheckBoxes.clear()
        templateOutputFields.clear()
        allTemplates.forEach { t ->
            val row = JPanel(java.awt.BorderLayout())
            val cb = JCheckBox(t.name, true)
            val pathField = TextFieldWithBrowseButton()
            val settings = project.service<GeneratorSettings>()
            val st = settings.state
            val remembered = st.templateOutputs?.get(t.name)
            pathField.text = remembered ?: t.outputPath
            pathField.textField.columns = 80
            pathField.preferredSize = java.awt.Dimension(JBUI.scale(700), JBUI.scale(28))
            pathField.maximumSize = java.awt.Dimension(Int.MAX_VALUE, JBUI.scale(28))
            pathField.toolTipText = "设置该模板的保存路径（支持占位符）"
            pathField.addBrowseFolderListener(
                "选择保存目录",
                null,
                project,
                com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
            )
            pathField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                private fun save() {
                    val m = st.templateOutputs ?: mutableMapOf()
                    m[t.name] = pathField.text
                    st.templateOutputs = m
                }
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            })
            templateCheckBoxes.add(cb)
            templateOutputFields.add(pathField)
            row.add(cb, java.awt.BorderLayout.WEST)
            val center = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
            center.add(JLabel("保存路径"))
            center.add(pathField)
            row.add(center, java.awt.BorderLayout.CENTER)
            templatePanel.add(row)
        }
        templatePanel.revalidate()
        templatePanel.repaint()
    }
    private fun buildPreview(): String {
        val first = selectedTableNames().firstOrNull() ?: initialSelectedNames.firstOrNull() ?: ""
        val prefix = tablePrefixField.text.trim()
        val baseName = if (prefix.isNotEmpty() && first.startsWith(prefix)) first.removePrefix(prefix) else first
        val entityName = toCamelUpper(baseName)
        val pkg = packageNameField.text.ifBlank { inferPackage() }
        val baseDir = baseDirField.text.ifBlank { project.basePath ?: "" }
        allTemplates = scanTemplates(templateRoot())
        val selected = selectedTemplates()
        val templates = if (selected.isNotEmpty()) selected else allTemplates
        val pkgPath = pkg.replace('.', '/')
        val sb = StringBuilder()
        templates.forEach { t ->
            var p = t.outputPath
            p = p.replace("\${baseDir}", baseDir)
            p = p.replace("\${packagePath}", pkgPath)
            p = p.replace("\${packageName}", pkg)
            p = p.replace("\${entityName}", entityName)
            p = p.replace("\${tableName}", first)
            sb.append(p).append('\n')
        }
        return sb.toString()
    }
    private fun toCamelUpper(name: String): String {
        val parts = name.lowercase().split('_')
        val b = StringBuilder()
        parts.forEach {
            if (it.isNotEmpty()) b.append(it[0].uppercaseChar()).append(it.substring(1))
        }
        return b.toString()
    }
    fun selectedTableNames(): List<String> = tableCheckboxes.filter { it.isSelected }.map { it.text }

    private fun refreshTableTabs() {
        val tables = selectedTableNames()
        tableTabs.removeAll()
        val tablesExcludeTabs = javax.swing.JTabbedPane()
        val tablesEnumTabs = javax.swing.JTabbedPane()
        tables.forEach { table ->
            val cols = fetchColumns(table)
            val dtoPanel = JPanel(); dtoPanel.layout = BoxLayout(dtoPanel, BoxLayout.Y_AXIS)
            val voPanel = JPanel(); voPanel.layout = BoxLayout(voPanel, BoxLayout.Y_AXIS)
            val dtoSel = tableDtoExcludeMap.getOrPut(table) { mutableSetOf() }
            val voSel = tableVoExcludeMap.getOrPut(table) { mutableSetOf() }
            val bothSel = tableBothExcludeMap.getOrPut(table) { mutableSetOf() }
            val excludeContent: java.awt.Component = if (applyBothCb.isSelected) {
                val bothPanel = JPanel(); bothPanel.layout = BoxLayout(bothPanel, BoxLayout.Y_AXIS)
                cols.forEach { cName ->
                    val bcb = JCheckBox(cName, bothSel.contains(cName))
                    bcb.addActionListener {
                        if (bcb.isSelected) {
                            bothSel.add(cName); dtoSel.add(cName); voSel.add(cName)
                        } else {
                            bothSel.remove(cName); dtoSel.remove(cName); voSel.remove(cName)
                        }
                    }
                    bothPanel.add(bcb)
                }
                JScrollPane(bothPanel)
            } else {
                cols.forEach { cName ->
                    val dcb = JCheckBox(cName, dtoSel.contains(cName))
                    val vcb = JCheckBox(cName, voSel.contains(cName))
                    dcb.addActionListener { if (dcb.isSelected) dtoSel.add(cName) else dtoSel.remove(cName) }
                    vcb.addActionListener { if (vcb.isSelected) voSel.add(cName) else voSel.remove(cName) }
                    dtoPanel.add(dcb)
                    voPanel.add(vcb)
                }
                val subTabs = javax.swing.JTabbedPane()
                subTabs.addTab("DTO", JScrollPane(dtoPanel))
                subTabs.addTab("VO", JScrollPane(voPanel))
                subTabs
            }
            tablesExcludeTabs.addTab(table, excludeContent)
            val enumPanel = JPanel(); enumPanel.layout = BoxLayout(enumPanel, BoxLayout.Y_AXIS)
            val enumSel = tableEnumFieldsMap.getOrPut(table) { mutableSetOf() }
            cols.forEach { cName ->
                val ecb = JCheckBox(cName, enumSel.contains(cName))
                ecb.addActionListener { if (ecb.isSelected) enumSel.add(cName) else enumSel.remove(cName) }
                enumPanel.add(ecb)
            }
            tablesEnumTabs.addTab(table, JScrollPane(enumPanel))
        }
        tableTabs.addTab("排除字段", tablesExcludeTabs)
        tableTabs.addTab("枚举字段", tablesEnumTabs)
        tableTabs.revalidate(); tableTabs.repaint()
    }

    private fun fetchAllTables(): List<String> {
        val facade = com.intellij.database.psi.DbPsiFacade.getInstance(project)
        val list = mutableSetOf<String>()
        facade.dataSources.forEach { src ->
            for (t in com.intellij.database.util.DasUtil.getTables(src.delegate)) list += t.name
        }
        return list.toList().sorted()
    }

    

    private fun loadSelectedTemplateRaw(): String { return "" }

    private fun selectedTemplates(): List<TemplateItem> {
        if (allTemplates.isEmpty()) allTemplates = scanTemplates(templateRoot())
        val list = mutableListOf<TemplateItem>()
        templateCheckBoxes.forEachIndexed { i, cb ->
            if (cb.isSelected && i < allTemplates.size && i < templateOutputFields.size) {
                val base = allTemplates[i]
                list.add(TemplateItem(base.name, base.engine, base.content, base.file, base.outputPath))
            }
        }
        return list
    }

    private fun parseMapping(text: String): Map<String, String> {
        val m = linkedMapOf<String, String>()
        text.lines().forEach { line ->
            val s = line.trim()
            if (s.isEmpty()) return@forEach
            val i = s.indexOf('=')
            if (i <= 0) return@forEach
            val k = s.substring(0, i).trim()
            val v = s.substring(i + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) m[k] = v
        }
        return m
    }
}
