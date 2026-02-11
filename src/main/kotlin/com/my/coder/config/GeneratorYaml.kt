package com.my.coder.config

/**
 * 生成配置：涵盖数据源、包名/目录、模板集合、表过滤、作者与类型覆盖，
 * 以及按表的 DTO/VO/共同排除字段与是否生成 Mapper/MapperXml 开关。
 *
 * @property database 数据库连接配置
 * @property packageName 生成代码的基础包名
 * @property baseDir 生成代码的基准目录
 * @property templates 模板项列表
 * @property tables 表过滤配置
 * @property author 作者信息
 * @property dtoExclude DTO基础排除字段列表
 * @property voExclude VO基础排除字段列表
 * @property typeOverride 类型覆盖映射（数据库类型 -> Java类型）
 * @property columnTypeOverride 列类型覆盖映射（列名 -> Java类型）
 * @property useLombok 是否使用Lombok注解
 * @property generateMapper 是否生成Mapper接口
 * @property generateMapperXml 是否生成Mapper XML文件
 * @property tableDtoExclude 按表的DTO排除字段映射
 * @property tableVoExclude 按表的VO排除字段映射
 * @property tableBothExclude 按表的共同排除字段映射
 * @property excludeApplyBoth 排除字段是否同时应用于DTO和VO
 * @property applyMapperMapping 是否应用映射配置
 * @property stripTablePrefix 表名前缀剥离配置
 * @property tableEnumFields 按表的枚举字段映射
 * @property templateDirOverrides 模板目录覆盖映射
 * @property templateFileNameOverrides 模板文件名覆盖映射
 * @property templateExcludeTemplates 模板排除列表
 * @property enumTemplateName 枚举模板名称
 * @property tableEnumTemplateOverrides 按表的枚举模板覆盖映射
 * @property tableEnumOutputDirOverrides 按表的枚举输出目录覆盖映射
 * @property titleEmptyImplementTemplates 标题空实现模板映射
 * @property tableEnabledTemplates 按表启用的模板映射
 * @property tableTemplateDirOverrides 按表的模板目录覆盖映射
 * @property tableTemplateFileNameOverrides 按表的模板文件名覆盖映射
 * @property tableTitleEmptyImplementTemplates 按表的标题空实现模板映射
 * @property tableTemplateExcludeTemplates 按表的模板排除映射
 * @property tableTemplateColumnExcludes 按表的模板列排除映射
 * @since 1.0.0
 * @author Neo
 */
data class GeneratorConfig(
    val database: Database,
    val packageName: String,
    val baseDir: String?,
    val templates: List<TemplateItem>,
    val tables: Tables,
    val author: String?,
    val dtoExclude: List<String>?,
    val voExclude: List<String>?,
    val typeOverride: Map<String, String>? = null,
    val columnTypeOverride: Map<String, String>? = null,
    val useLombok: Boolean = false,
    val generateMapper: Boolean = true,
    val generateMapperXml: Boolean = true,
    val tableDtoExclude: Map<String, List<String>>? = null,
    val tableVoExclude: Map<String, List<String>>? = null,
    val tableBothExclude: Map<String, List<String>>? = null,
    val excludeApplyBoth: Boolean = false,
    val applyMapperMapping: Boolean = true,
    val stripTablePrefix: String? = null,
    val tableEnumFields: Map<String, List<String>>? = null,
    val templateDirOverrides: Map<String, String>? = null,
    val templateFileNameOverrides: Map<String, String>? = null,
    val templateExcludeTemplates: List<String>? = null
    ,
    val enumTemplateName: String? = null,
    val tableEnumTemplateOverrides: Map<String, Map<String, String>>? = null,
    val tableEnumOutputDirOverrides: Map<String, Map<String, String>>? = null,
    val titleEmptyImplementTemplates: Map<String, Boolean>? = null,
    val tableEnabledTemplates: Map<String, List<String>>? = null,
    val tableTemplateDirOverrides: Map<String, Map<String, String>>? = null,
    val tableTemplateFileNameOverrides: Map<String, Map<String, String>>? = null,
    val tableTitleEmptyImplementTemplates: Map<String, Map<String, Boolean>>? = null,
    val tableTemplateExcludeTemplates: Map<String, List<String>>? = null,
    val tableTemplateColumnExcludes: Map<String, Map<String, List<String>>>? = null
)

/**
 * 数据库连接配置（YAML 可选，缺省时使用 IDEA Database 数据源）。
 *
 * @property url 数据库连接URL
 * @property username 数据库用户名
 * @property password 数据库密码
 * @property driver 数据库驱动类名
 * @since 1.0.0
 */
data class Database(
    val url: String,
    val username: String,
    val password: String,
    val driver: String
)

/**
 * 模板项：名称、引擎、内联内容或文件路径、输出路径模式。
 *
 * @property name 模板名称
 * @property engine 模板引擎类型
 * @property content 模板内容（内联）
 * @property file 模板文件路径
 * @property outputPath 输出路径模式
 * @property fileType 文件类型，默认为"java"
 * @since 1.0.0
 */
data class TemplateItem(
    val name: String,
    val engine: String,
    val content: String?,
    val file: String?,
    val outputPath: String,
    val fileType: String = "java"
)

/**
 * 表过滤：include 优先于 exclude。
 *
 * @property include 包含的表名列表
 * @property exclude 排除的表名列表
 * @since 1.0.0
 */
data class Tables(
    val include: List<String>?,
    val exclude: List<String>?
)

/**
 * 列元数据：包含数据库类型、可空、主键、映射后的 Java 类型与注释。
 *
 * @property name 列名
 * @property type 数据库类型
 * @property nullable 是否可空
 * @property primaryKey 是否为主键
 * @property javaType 映射后的Java类型
 * @property nameCamel 小驼峰命名的列名
 * @property size 列大小
 * @property comment 列注释
 * @since 1.0.0
 */
data class ColumnMeta(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val javaType: String,
    val nameCamel: String,
    val size: Int?,
    val comment: String?
)

/**
 * 表元数据：包含实体名、列集合与注释。
 *
 * @property name 表名
 * @property entityName 实体类名（驼峰命名）
 * @property columns 列元数据列表
 * @property comment 表注释
 * @since 1.0.0
 */
data class TableMeta(
    val name: String,
    val entityName: String,
    val columns: List<ColumnMeta>,
    val comment: String?
)
