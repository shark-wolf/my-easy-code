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
class QuickGenerateDialog(private val project: Project, private val initialSelectedNames: List<String>, private val leftTitle: String? = null, private val leftTables: List<String>? = null) : DialogWrapper(project) {
    private val baseDirField = TextFieldWithBrowseButton()
    private val packageNameField = TextFieldWithBrowseButton()
    private val tablePrefixField = JBTextField()
    
    private val templateRootField = TextFieldWithBrowseButton()
    private val templateCheckBoxes = mutableListOf<JCheckBox>()
    private val templateOutputFields = mutableListOf<TextFieldWithBrowseButton>()
    private val templateFileNameFields = mutableListOf<JBTextField>()
    private val templateExcludeFlags = mutableListOf<JCheckBox>()
    private lateinit var templatePanel: JPanel
    private var allTemplates: List<TemplateItem> = emptyList()
    private val typeOverrideField = JTextArea(4, 40)
    private val columnTypeOverrideField = JTextArea(4, 40)
    private val importBtn = JButton("导入插件模板到项目根目录")
    private val tableCheckboxes = mutableListOf<JCheckBox>()
    private val applyBothCb = JCheckBox("排除字段一起生效（限已选中排除的模板）", false)
    private val tableTabs = javax.swing.JTabbedPane()
    private val tableDtoExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableVoExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableBothExcludeMap = mutableMapOf<String, MutableSet<String>>()
    private val tableEnumFieldsMap = mutableMapOf<String, MutableSet<String>>()
    private var vfsListener: com.intellij.openapi.vfs.VirtualFileListener? = null
    private var enumTemplateCombo: javax.swing.JComboBox<String>? = null
    private val tableEnumTplMap = mutableMapOf<String, MutableMap<String, String>>()
    private val tableEnumOutDirMap = mutableMapOf<String, MutableMap<String, String>>()
    
    
    
    private val useLombokCb = JCheckBox("使用 Lombok", true)
    
    init {
        title = "My Easy Code Quick Generate"
        isModal = true
        setOKButtonText("Create")
        setCancelButtonText("Cancel")
        baseDirField.text = project.basePath ?: ""
        val root = project.basePath?.let { Path.of(it).resolve("my-easy-code").resolve("templates").resolve("general").toString() } ?: ""
        templateRootField.text = root
        
        
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
        val leftNames = leftTables ?: fetchAllTables()
        leftNames.forEach { name ->
            val cb = JCheckBox(name, preselect.isEmpty() || preselect.contains(name))
            cb.addActionListener { refreshTableTabs() }
            tableCheckboxes.add(cb)
            left.add(cb)
        }
        left.border = BorderFactory.createTitledBorder(leftTitle ?: "数据表")
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
        templatesScroll.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        templatesScroll.verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
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
        importBtn.toolTipText = "将插件内置模板复制到 my-easy-code/templates/general，并创建 my-easy-code/templates/enums"
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
        applyBothCb.addActionListener {
            if (applyBothCb.isSelected) {
                unifyExcludesAcrossTabs()
                refreshTableTabs()
            }
        }
        typeOverrideField.toolTipText = "按数据库类型覆盖 Java 类型，每行 type=JavaType"
        columnTypeOverrideField.toolTipText = "按列名覆盖 Java 类型，每行 column=JavaType"
        importBtn.addActionListener {
            val base = project.basePath ?: return@addActionListener
            val tplRoot = Path.of(base).resolve("my-easy-code").resolve("templates")
            val destGeneral = tplRoot.resolve("general")
            val enumsDir = tplRoot.resolve("enums")
            try { Files.createDirectories(destGeneral) } catch (_: Throwable) {}
            try { Files.createDirectories(enumsDir) } catch (_: Throwable) {}
            val names = builtInTemplateBaseNames()
            names.forEach { n ->
                val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
                if (inStream != null) {
                    val target = destGeneral.resolve("$n.ftl")
                    Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            run {
                val enumStream = javaClass.classLoader.getResourceAsStream("templates/enum.ftl")
                if (enumStream != null) {
                    val target = enumsDir.resolve("enum.ftl")
                    Files.copy(enumStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            refreshTemplateList()
            val vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tplRoot.toFile())
            vDir?.refresh(false, true)
            ProjectView.getInstance(project).refresh()
            run {
                val msg = "模板已导入到 my-easy-code/templates/general 与 templates/enums"
                com.intellij.openapi.ui.Messages.showYesNoDialog(
                    project,
                    msg,
                    "导入成功",
                    "确认",
                    "取消",
                    null
                )
                importBtn.transferFocus()
            }
        }
        val vm = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
        vfsListener = object : com.intellij.openapi.vfs.VirtualFileListener {
            override fun fileCreated(event: com.intellij.openapi.vfs.VirtualFileEvent) {
                val root = templateRoot().toString().replace('\\','/')
                val p = event.file.path.replace('\\','/')
                if (p.startsWith(root)) refreshTemplateList()
            }
            override fun fileDeleted(event: com.intellij.openapi.vfs.VirtualFileEvent) {
                val root = templateRoot().toString().replace('\\','/')
                val p = event.file.path.replace('\\','/')
                if (p.startsWith(root)) refreshTemplateList()
            }
            override fun contentsChanged(event: com.intellij.openapi.vfs.VirtualFileEvent) {
                val root = templateRoot().toString().replace('\\','/')
                val p = event.file.path.replace('\\','/')
                if (p.startsWith(root)) refreshTemplateList()
            }
            override fun fileMoved(event: com.intellij.openapi.vfs.VirtualFileMoveEvent) {
                val root = templateRoot().toString().replace('\\','/')
                val p = event.file.path.replace('\\','/')
                if (p.startsWith(root)) refreshTemplateList()
            }
        }
        vm.addVirtualFileListener(vfsListener!!)
        return wrapper
    }
    private fun builtInTemplateBaseNames(): List<String> {
        return try {
            val cl = javaClass.classLoader
            val url = cl.getResource("templates")
            if (url != null) {
                when (url.protocol.lowercase()) {
                    "file" -> {
                        val dir = java.nio.file.Paths.get(url.toURI())
                        java.nio.file.Files.list(dir).use { stream ->
                            stream.filter { java.nio.file.Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
                                .map { it.fileName.toString().substringBeforeLast('.') }
                                .toList()
                        }
                    }
                    "jar" -> {
                        val s = url.toString()
                        val bang = s.indexOf("!/")
                        if (bang > 0) {
                            val jarUrl = s.substring(4, bang) // remove "jar:"
                            val fUrl = java.net.URL(jarUrl)
                            val jarFile = java.nio.file.Paths.get(fUrl.toURI()).toFile()
                            val names = mutableListOf<String>()
                            java.util.jar.JarFile(jarFile).use { jf ->
                                val en = jf.entries()
                                while (en.hasMoreElements()) {
                                    val e = en.nextElement()
                                    val n = e.name
                                    if (n.startsWith("templates/") && n.lowercase().endsWith(".ftl")) {
                                        val base = java.nio.file.Paths.get(n).fileName.toString().substringBeforeLast('.')
                                        names += base
                                    }
                                }
                            }
                            names
                        } else emptyList()
                    }
                    else -> emptyList()
                }
            } else emptyList()
        } catch (_: Throwable) {
            emptyList()
        }.ifEmpty { listOf("entity","mapper","mapperXml","service","serviceImpl","controller","dto","vo") }
    }
    override fun dispose() {
        val vm = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
        val l = vfsListener
        if (l != null) vm.removeVirtualFileListener(l)
        vfsListener = null
        super.dispose()
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
        val templates = selected.filterNot { it.name.equals("enum", true) }
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
        val fileNameOverrides = settings.state.templateFileNames?.toMap()
        val excludeTplNames = templateCheckBoxes.mapIndexedNotNull { idx, cb ->
            if (cb.isSelected && idx < templateExcludeFlags.size && templateExcludeFlags[idx].isSelected && idx < allTemplates.size) allTemplates[idx].name else null
        }
        val enumTplName = project.service<GeneratorSettings>().state.enumTemplateName
        val enumTplOverrides = tableEnumTplMap.mapValues { it.value.toMap() }.filterValues { it.isNotEmpty() }
        val enumOutDirOverrides = tableEnumOutDirMap.mapValues { it.value.toMap() }.filterValues { it.isNotEmpty() }
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
            tableDtoExclude = if (tableDtoEx.isNotEmpty()) tableDtoEx else null,
            tableVoExclude = if (tableVoEx.isNotEmpty()) tableVoEx else null,
            tableBothExclude = if (tableBothEx.isNotEmpty()) tableBothEx else null,
            excludeApplyBoth = applyBothCb.isSelected,
            applyMapperMapping = false,
            stripTablePrefix = tablePrefixField.text.trim().ifBlank { null },
            tableEnumFields = if (enumSel.isNotEmpty()) enumSel else null,
            templateDirOverrides = dirOverrides,
            templateFileNameOverrides = fileNameOverrides,
            templateExcludeTemplates = excludeTplNames,
            enumTemplateName = enumTplName,
            tableEnumTemplateOverrides = if (enumTplOverrides.isNotEmpty()) enumTplOverrides else null,
            tableEnumOutputDirOverrides = if (enumOutDirOverrides.isNotEmpty()) enumOutDirOverrides else null
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
        return Path.of(base).resolve("my-easy-code").resolve("templates").resolve("general")
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
                    val name = f.fileName.toString().substringBeforeLast('.')
                    val rel = try { root.relativize(f).toString() } catch (_: Exception) { f.toString() }
                    val ft = defaultTypeFor(name)
                    val out = suggestOutput(name, ft)
                    list.add(TemplateItem(name, "freemarker", null, rel, out, ft))
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
    private fun suggestOutput(name: String, fileType: String): String {
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
    private fun defaultTypeFor(name: String): String = if (name.equals("mapperXml", true)) "xml" else "java"
    private fun extensionFor(fileType: String): String = when (fileType.lowercase()) {
        "java" -> "java"
        "xml" -> "xml"
        "kotlin", "kt" -> "kt"
        "groovy", "gvy" -> "groovy"
        else -> fileType.lowercase()
    }
    private fun inferPackage(): String {
        val base = project.basePath ?: return ""
        val p = Path.of(base).resolve("src/main/java")
        if (!Files.exists(p)) return ""
        return "com.example"
    }
    private fun refreshTemplateList() {
        allTemplates = scanTemplates(templateRoot()).filterNot { it.name.equals("enum", true) }
        if (allTemplates.isEmpty()) {
            val base = project.basePath
            if (base != null) {
                val tplRoot = Path.of(base).resolve("my-easy-code").resolve("templates")
                val destGeneral = tplRoot.resolve("general")
                val enumsDir = tplRoot.resolve("enums")
                try { Files.createDirectories(destGeneral) } catch (_: Throwable) {}
                try { Files.createDirectories(enumsDir) } catch (_: Throwable) {}
                val names = builtInTemplateBaseNames()
                names.forEach { n ->
                    val inStream = javaClass.classLoader.getResourceAsStream("templates/$n.ftl")
                    if (inStream != null) {
                        val target = destGeneral.resolve("$n.ftl")
                        try { Files.copy(inStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) } catch (_: Throwable) {}
                    }
                }
                run {
                    val enumStream = javaClass.classLoader.getResourceAsStream("templates/enum.ftl")
                    if (enumStream != null) {
                        val target = enumsDir.resolve("enum.ftl")
                        try { Files.copy(enumStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) } catch (_: Throwable) {}
                    }
                }
                allTemplates = scanTemplates(templateRoot()).filterNot { it.name.equals("enum", true) }
            }
        }
        templatePanel.removeAll()
        templateCheckBoxes.clear()
        templateOutputFields.clear()
        templateFileNameFields.clear()
        templateExcludeFlags.clear()
        allTemplates.forEach { t ->
            val row = JPanel(java.awt.BorderLayout())
            val cb = JCheckBox(t.name, true)
            val pathField = TextFieldWithBrowseButton()
            val fileNameField = JBTextField()
            pathField.textField.columns = 30
            pathField.preferredSize = java.awt.Dimension(JBUI.scale(380), JBUI.scale(28))
            pathField.maximumSize = java.awt.Dimension(JBUI.scale(450), JBUI.scale(28))
            val settings = project.service<GeneratorSettings>()
            val st = settings.state
            val remembered = st.templateOutputs?.get(t.name)
            pathField.text = remembered ?: t.outputPath
            pathField.textField.columns = 36
            pathField.preferredSize = java.awt.Dimension(JBUI.scale(520), JBUI.scale(28))
            pathField.maximumSize = java.awt.Dimension(JBUI.scale(520), JBUI.scale(28))
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
            val rememberedFile = st.templateFileNames?.get(t.name)
            val ext = extensionFor(t.fileType)
            val baseName = if (t.name.equals("mapperXml", true)) "mapper" else t.name
            val defName = "\${entityName}" + baseName + "." + ext
            fileNameField.text = (rememberedFile?.takeIf { it.isNotBlank() } ?: defName)
            fileNameField.columns = 24
            fileNameField.preferredSize = java.awt.Dimension(JBUI.scale(320), JBUI.scale(28))
            fileNameField.maximumSize = java.awt.Dimension(JBUI.scale(320), JBUI.scale(28))
            fileNameField.toolTipText = "设置生成文件名（不含目录）"
            fileNameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                private fun save() {
                    val m = st.templateFileNames ?: mutableMapOf()
                    m[t.name] = fileNameField.text.trim()
                    st.templateFileNames = m
                }
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { save() }
            })
            templateCheckBoxes.add(cb)
            templateOutputFields.add(pathField)
            templateFileNameFields.add(fileNameField)
            val excludeCb = JCheckBox("", false)
            excludeCb.toolTipText = "勾选后该模板参与排除字段设置"
            templateExcludeFlags.add(excludeCb)
            cb.addActionListener { refreshTableTabs() }
            excludeCb.addActionListener { refreshTableTabs() }
            row.add(cb, java.awt.BorderLayout.WEST)
            val center = JPanel()
            center.layout = BoxLayout(center, BoxLayout.X_AXIS)
            center.add(JLabel("保存路径"))
            center.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(8)))
            center.add(pathField)
            center.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(8)))
            center.add(JLabel("文件名"))
            center.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(8)))
            center.add(fileNameField)
            center.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(16)))
            center.add(JLabel("排除字段"))
            center.add(javax.swing.Box.createHorizontalStrut(JBUI.scale(4)))
            center.add(excludeCb)
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
        val templates = selected
        val pkgPath = pkg.replace('.', '/')
        val settings = project.service<GeneratorSettings>()
        val sb = StringBuilder()
        templates.forEach { t ->
            var p = t.outputPath
            p = p.replace("\${baseDir}", baseDir)
            p = p.replace("\${packagePath}", pkgPath)
            p = p.replace("\${packageName}", pkg)
            p = p.replace("\${entityName}", entityName)
            p = p.replace("\${tableName}", baseName)
            val rememberedFile = settings.state.templateFileNames?.get(t.name)
            val ext = extensionFor(t.fileType)
            val base = if (t.name.equals("mapperXml", true)) "mapper" else t.name
            val defFile = entityName + base + "." + ext
            val fileNameRaw = (rememberedFile ?: defFile)
            val fileName = fileNameRaw.replace("\${entityName}", entityName).replace("\${tableName}", first)
            val dirOnly = java.nio.file.Path.of(p).parent?.toString() ?: p
            val finalPath = java.nio.file.Path.of(dirOnly).resolve(fileName).toString()
            sb.append(finalPath).append('\n')
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
    private fun unifyExcludesAcrossTabs() {
        val tables = selectedTableNames()
        tables.forEach { table ->
            val dtoSel = tableDtoExcludeMap.getOrPut(table) { mutableSetOf() }
            val voSel = tableVoExcludeMap.getOrPut(table) { mutableSetOf() }
            val bothSel = tableBothExcludeMap.getOrPut(table) { mutableSetOf() }
            val union = mutableSetOf<String>()
            union.addAll(dtoSel); union.addAll(voSel); union.addAll(bothSel)
            dtoSel.clear(); dtoSel.addAll(union)
            voSel.clear(); voSel.addAll(union)
            bothSel.clear(); bothSel.addAll(union)
        }
    }
    fun selectedTableNames(): List<String> = tableCheckboxes.filter { it.isSelected }.map { it.text }

    private fun refreshTableTabs() {
        val tables = selectedTableNames()
        tableTabs.removeAll()
        val tablesExcludeTabs = javax.swing.JTabbedPane()
        val tablesEnumTabs = javax.swing.JTabbedPane()
        val chosenTemplateNames = templateCheckBoxes.mapIndexedNotNull { idx, cb ->
            if (cb.isSelected && idx < templateExcludeFlags.size && templateExcludeFlags[idx].isSelected && idx < allTemplates.size) allTemplates[idx].name else null
        }
        tables.forEach { table ->
            val cols = fetchColumns(table)
            val dtoSel = tableDtoExcludeMap.getOrPut(table) { mutableSetOf() }
            val voSel = tableVoExcludeMap.getOrPut(table) { mutableSetOf() }
            val bothSel = tableBothExcludeMap.getOrPut(table) { mutableSetOf() }
            val subTabs = javax.swing.JTabbedPane()
            fun onToggle(tplName: String, col: String, selected: Boolean) {
                if (applyBothCb.isSelected) {
                    if (selected) {
                        bothSel.add(col); dtoSel.add(col); voSel.add(col)
                    } else {
                        bothSel.remove(col); dtoSel.remove(col); voSel.remove(col)
                    }
                    val union = mutableSetOf<String>().apply {
                        addAll(dtoSel); addAll(voSel); addAll(bothSel)
                    }
                    for (i in 0 until subTabs.tabCount) {
                        val comp = subTabs.getComponentAt(i)
                        val scroll = comp as? JScrollPane
                        val panel = (scroll?.viewport?.view as? JPanel) ?: continue
                        for (j in 0 until panel.componentCount) {
                            val c0 = panel.getComponent(j)
                            if (c0 is JCheckBox) {
                                c0.isSelected = union.contains(c0.text)
                            }
                        }
                    }
                } else {
                    when (tplName.lowercase()) {
                        "dto" -> if (selected) dtoSel.add(col) else dtoSel.remove(col)
                        "vo" -> if (selected) voSel.add(col) else voSel.remove(col)
                        else -> if (selected) bothSel.add(col) else bothSel.remove(col)
                    }
                }
            }
            chosenTemplateNames.forEach { name ->
                val panel = JPanel(); panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
                cols.forEach { c ->
                    val initSelected = when (name.lowercase()) {
                        "dto" -> dtoSel.contains(c)
                        "vo" -> voSel.contains(c)
                        else -> bothSel.contains(c)
                    }
                    val cb = JCheckBox(c, initSelected)
                    cb.addActionListener { onToggle(name, c, cb.isSelected) }
                    panel.add(cb)
                }
                run {
                    val sp = JScrollPane(panel)
                    sp.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    subTabs.addTab(name, sp)
                }
            }
            run {
                val lockIdx = intArrayOf(0)
                subTabs.addChangeListener {
                    if (applyBothCb.isSelected) {
                        subTabs.selectedIndex = lockIdx[0]
                    } else {
                        lockIdx[0] = subTabs.selectedIndex
                    }
                }
            }
            tablesExcludeTabs.addTab(table, subTabs)
            val enumPanel = JPanel(); enumPanel.layout = BoxLayout(enumPanel, BoxLayout.Y_AXIS)
            val enumSel = tableEnumFieldsMap.getOrPut(table) { mutableSetOf() }
            val enumTpls = tableEnumTplMap.getOrPut(table) { mutableMapOf() }
            val enumOutDirs = tableEnumOutDirMap.getOrPut(table) { mutableMapOf() }
            val opts = listEnumTemplates()
            val settingsState = project.service<GeneratorSettings>().state
            val pkg = packageNameField.text.ifBlank { inferPackage() }
            val baseDir = baseDirField.text.ifBlank { project.basePath ?: "" }
            val pkgPath = pkg.replace('.', '/')
            val defaultEnumDir = java.nio.file.Path.of(baseDir).resolve("src/main/java").resolve(pkgPath).resolve("enums").toString()
            cols.forEach { cName ->
                val row = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0))
                val ecb = JCheckBox(cName, enumSel.contains(cName))
                val combo = javax.swing.JComboBox(opts.toTypedArray())
                combo.toolTipText = "选择枚举模板"
                val lbl = JLabel("选择枚举模板")
                val remember = enumTpls[cName] ?: settingsState.enumTemplateName
                if (!remember.isNullOrBlank()) combo.selectedItem = remember
                combo.isVisible = ecb.isSelected
                lbl.isVisible = ecb.isSelected
                val dirLbl = JLabel("保存路径")
                val dirField = com.intellij.openapi.ui.TextFieldWithBrowseButton()
                dirField.text = enumOutDirs[cName] ?: defaultEnumDir
                dirField.textField.columns = 30
                dirField.preferredSize = java.awt.Dimension(JBUI.scale(380), JBUI.scale(28))
                dirField.maximumSize = java.awt.Dimension(JBUI.scale(450), JBUI.scale(28))
                dirField.addBrowseFolderListener(
                    "选择保存目录",
                    null,
                    project,
                    com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                dirField.isVisible = ecb.isSelected
                dirLbl.isVisible = ecb.isSelected
                ecb.addActionListener {
                    if (ecb.isSelected) enumSel.add(cName) else enumSel.remove(cName)
                    combo.isVisible = ecb.isSelected
                    lbl.isVisible = ecb.isSelected
                    dirField.isVisible = ecb.isSelected
                    dirLbl.isVisible = ecb.isSelected
                }
                combo.addActionListener {
                    val sel = combo.selectedItem?.toString()
                    if (!sel.isNullOrBlank()) enumTpls[cName] = sel
                }
                dirField.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    private fun save() {
                        enumOutDirs[cName] = dirField.text.trim()
                    }
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { save() }
                })
                row.add(ecb)
                row.add(lbl)
                row.add(combo)
                row.add(dirLbl)
                row.add(dirField)
                enumPanel.add(row)
            }
            run {
                val sp = JScrollPane(enumPanel)
                sp.horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                tablesEnumTabs.addTab(table, sp)
            }
        }
        tableTabs.addTab("排除字段", tablesExcludeTabs)
        tableTabs.addTab("枚举字段", tablesEnumTabs)
        tableTabs.revalidate(); tableTabs.repaint()
    }
    private fun listEnumTemplates(): List<String> {
        val base = project.basePath ?: return listOf("内置")
        val dir = Path.of(base).resolve("my-easy-code").resolve("templates").resolve("enums")
        if (!Files.exists(dir)) return listOf("内置")
        return try {
            Files.list(dir).use { s ->
                s.filter { Files.isRegularFile(it) && it.fileName.toString().lowercase().endsWith(".ftl") }
                    .map { it.fileName.toString() }
                    .toList()
                    .ifEmpty { listOf("内置") }
            }
        } catch (_: Throwable) {
            listOf("内置")
        }
    }

    private fun fetchAllTables(): List<String> {
        val facade = com.intellij.database.psi.DbPsiFacade.getInstance(project)
        val list = mutableSetOf<String>()
        facade.dataSources.forEach { src ->
            for (t in com.intellij.database.util.DasUtil.getTables(src.delegate)) list += t.name
        }
        return list.toList().sorted()
    }
    

    

    

    private fun selectedTemplates(): List<TemplateItem> {
        if (allTemplates.isEmpty()) allTemplates = scanTemplates(templateRoot())
        val list = mutableListOf<TemplateItem>()
        templateCheckBoxes.forEachIndexed { i, cb ->
            if (cb.isSelected && i < allTemplates.size && i < templateOutputFields.size) {
                val base = allTemplates[i]
                list.add(TemplateItem(base.name, base.engine, base.content, base.file, base.outputPath, base.fileType))
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
