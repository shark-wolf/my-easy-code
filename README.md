# my-easy-code (IntelliJ IDEA 插件)

一个可配置的 MyBatis 代码生成插件，支持从数据库元数据或 YAML 配置生成 Entity、Mapper、XML、Service、Controller、DTO、VO，以及 MapStruct 转换器，且可通过 GUI 管理多套模板方案与输出路径。

## 主要特性
- 自定义模板与输出路径：支持 Freemarker 模板，路径占位符 `${baseDir}`、`${packagePath}`、`${entityName}`、`${tableName}`
- 支持 YAML 配置与 IDEA GUI 配置：GUI 值优先，自动与 YAML 合并
- 从 IDEA Database 视图选表生成：多选表后生成指定表的代码
- DTO/VO 字段排除：`dtoExclude`、`voExclude` 精确过滤字段（列名/驼峰名）
- MyBatis-Plus 风格 Service：`IService<T>` 与 `ServiceImpl<Mapper, T>`
- MapStruct 转换器：自动生成 Entity ↔ DTO ↔ VO 映射
- 模板方案管理：GUI 中增删方案、切换 Active 方案；支持从目录扫描 `.ftl` 导入

## 目录结构
- `src/main/resources/META-INF/plugin.xml` 插件声明与菜单入口
- `src/main/kotlin/com/my/coder` 插件核心代码
  - `GenerateMyBatisCodeAction.kt` 从 YAML 入口生成
  - `GenerateFromIdeaDatabaseAction.kt` 从 Database 选表入口生成
  - `Generator.kt` 读取配置、拉取元数据、渲染模板与写文件
  - `settings/GeneratorSettings*.kt` GUI 配置与持久化、模板方案管理
  - `config/GeneratorYaml.kt` 配置与表元数据模型
  - `type/TypeMapper.kt` 数据库类型到 Java 类型映射
- `sample/generator.yaml` 示例配置
- `sample/templates/*.ftl` 示例模板（entity/mapper/xml/service/controller/dto/vo/convert）

## 快速开始
1. 用 IntelliJ 打开项目目录 `my-easy-code`（或当前根项目已对齐）
2. 设置 JDK 为 17：`File -> Project Structure -> Project -> SDK`
3. 在 Gradle 面板运行 `intellij -> runIde` 启动沙箱 IDEA，插件自动加载

## 在沙箱中体验
- 菜单 `Tools`：
  - `Generate MyBatis Code` 选择 `generator.yaml` 按模板生成
  - `Generate From IDEA Database` 在 Database 视图选中表后生成
- 设置页 `Settings/Preferences -> MyBatis Generator`：
  - 配置 `Package Name`、`Base Dir`、`Author`、`Template Root`
  - `Import Sample Templates` 导入示例模板；或 `Import From Directory` 扫描目录 `.ftl` 自动生成模板条目
  - 模板方案管理：增删方案、设为 Active，生成时优先使用 Active 方案模板

## YAML 配置示例
```yaml
database:
  url: jdbc:mysql://localhost:3306/demo?useSSL=false&serverTimezone=UTC
  username: root
  password: root
  driver: com.mysql.cj.jdbc.Driver
packageName: com.my.coder.demo
baseDir: ${baseDir}
author: demo
dtoExclude:
  - password
voExclude:
  - password
tables:
  include:
    - user
templates:
  - name: entity
    engine: freemarker
    file: templates/entity.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/entity/${entityName}.java
  - name: mapper
    engine: freemarker
    file: templates/mapper.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/mapper/${entityName}Mapper.java
  - name: mapperXml
    engine: freemarker
    file: templates/mapperXml.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/mapper/xml/${entityName}Mapper.xml
  - name: service
    engine: freemarker
    file: templates/service.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/service/${entityName}Service.java
  - name: serviceImpl
    engine: freemarker
    file: templates/serviceImpl.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/service/impl/${entityName}ServiceImpl.java
  - name: controller
    engine: freemarker
    file: templates/controller.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/controller/${entityName}Controller.java
  - name: dto
    engine: freemarker
    file: templates/dto.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/dto/${entityName}DTO.java
  - name: vo
    engine: freemarker
    file: templates/vo.ftl
    outputPath: ${baseDir}/src/main/java/${packagePath}/vo/${entityName}VO.java
```

## mapper.yml 类型映射与枚举生成
- 路径：`my-easy-code/mapper/mapper.yml`
- 结构：
```yaml
mappings:
  - tableName: user
    column: status
    Java:
      type: ${packageName}.mapper.Status
      name: Status
      savePath: ${baseDir}/src/main/java/${packagePath}/mapper/Status.java
      enum:
        values: [ENABLED, DISABLED]
```
- 说明：
  - `type`: 列映射使用的 Java 类型（可为枚举的全限定名），优先于默认类型映射
  - `name`: 生成类/枚举的名称
  - `savePath`: 生成类/枚举的输出路径（支持 `${baseDir}` `${projectBase}` `${packagePath}` `${packageName}`）
  - `enum.values`: 当提供该数组时，生成枚举类型；否则生成空类骨架

## 模板变量与占位符
- 模板数据：
  - `packageName`、`author`、`date`、`entityName`
  - `table.name`、`table.columns[]`（列：`name`、`javaType`、`nameCamel`、`nullable`、`primaryKey`）
  - `exclude` 用于 DTO/VO 过滤，或使用 `dtoExclude`/`voExclude`
- 输出路径占位符：
  - `${baseDir}` 生成根目录（默认项目根）
  - `${packagePath}` 包路径（如 `com.my.demo` → `com/my/demo`）
  - `${entityName}`、`${tableName}`

## 从 IDEA Database 视图生成
- 配置数据源并连接，选中多个表
- 执行 `Tools -> Generate From IDEA Database`，选择 `generator.yaml`
- 所选表名覆盖 YAML 的 `tables.include`

## MapStruct 转换器
- 生成接口：`${entityName}Convert`（`@Mapper(componentModel = "spring")`）
- 方法：`toEntity(dto)`、`toVO(entity)`、`toVOList(list)`
- 根据 `dtoExclude/voExclude` 自动在转换器上添加 `@Mapping(ignore = true)`

## 打包与安装
- Gradle 面板运行 `buildPlugin`，输出 `build/distributions/*.zip`
- 在主 IDEA 中 `Settings -> Plugins -> Install Plugin from Disk...` 安装 zip 并重启

## 依赖说明
- 目标项目需引入：MyBatis-Plus、Lombok、MapStruct（含注解处理器）
- 插件内部已包含：SnakeYAML、Freemarker、MySQL 驱动；使用其他数据库请在 YAML 修改驱动并在目标项目引入对应驱动

## 常见问题
- 菜单不出现：先运行 `runIde`，或 Gradle 同步后 `Invalidate Caches / Restart...`
- 模板未找到：检查设置页 `Template Root`，模板 `file` 相对该目录解析
- DTO/VO 字段过滤无效：确认 `dtoExclude/voExclude` 使用列名或驼峰名，与数据库实际字段一致

## 许可
本项目用于示例与内部使用，如需开源协议请按需添加。
