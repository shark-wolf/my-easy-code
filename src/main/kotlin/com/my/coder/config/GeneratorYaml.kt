package com.my.coder.config

/**
 * 生成配置：涵盖数据源、包名/目录、模板集合、表过滤、作者与类型覆盖，
 * 以及按表的 DTO/VO/共同排除字段与是否生成 Mapper/MapperXml 开关。
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
    val tableTemplateExcludeTemplates: Map<String, List<String>>? = null
)

/**
 * 数据库连接配置（YAML 可选，缺省时使用 IDEA Database 数据源）。
 */
data class Database(
    val url: String,
    val username: String,
    val password: String,
    val driver: String
)

/**
 * 模板项：名称、引擎、内联内容或文件路径、输出路径模式。
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
 */
data class Tables(
    val include: List<String>?,
    val exclude: List<String>?
)

/**
 * 列元数据：包含数据库类型、可空、主键、映射后的 Java 类型与注释。
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
 */
data class TableMeta(
    val name: String,
    val entityName: String,
    val columns: List<ColumnMeta>,
    val comment: String?
)
