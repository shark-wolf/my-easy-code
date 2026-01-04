# my-easy-code (IntelliJ IDEA 插件)

一个可配置的 MyBatis 代码生成插件。支持从数据库元数据或配置生成 Entity、Mapper、XML、Service、Controller、DTO、VO，并提供可视化模板选择、预览与输出路径控制。


## 目录结构
- `src/main/resources/META-INF/plugin.xml` 插件声明与入口
- `src/main/kotlin/com/my/coder`
  - `QuickGenerateDialog.kt` 生成弹框（选择表、模板、路径、排除/枚举）
  - `Generator.kt` 读取配置、渲染模板、写文件与统计
  - `PreviewTemplateAction.kt` 模板右键预览
  - `TemplatePreviewNotificationProvider.kt` 编辑器右上角“预览”浮动按钮
  - `TemplatePreview.kt` 统一的预览渲染逻辑（弹框）
  - `config/GeneratorYaml.kt` 配置模型（含 `templateDirOverrides`、`tableEnumFields`）
  - `settings/GeneratorSettings*.kt` 设置项与持久化

-## 快速开始
- 启动沙箱：`./gradlew.bat runIde`
- 菜单使用：
-  - `Tools -> Generate From IDEA Database`（在 Database 视图选中表后生成）
- 弹框使用：
  - 选择表、填写包名与基准目录
  - 在模板选择区为每个模板设置“保存路径”（仅目录）
  - 在“排除字段”和“枚举字段”Tab分别按表勾选
  - 点击 `Create` 开始生成

## 路径与包名
- 保存路径仅覆盖目录：若输入路径末段包含`.`（看起来像文件），仅使用其父目录作为保存目录
- 包名计算：`package` 基于最终保存目录相对 `src/main/java` 的路径；模板内使用 `filePackage`
- 模板跨文件导入：通过 `dtoPackage/voPackage/entityPackage/servicePackage/mapperPackage/controllerPackage/convertPackage` 变量保证导入正确

## 模板支持的变量
- 通用模板数据
  - packageName：全局包名
  - filePackage：根据输出目录计算的文件包名
  - entityName：实体类名（去除表前缀后转驼峰）
  - table：表元数据对象（TableMeta），字段：
    - name：表名
    - entityName：同上
    - comment：表注释
    - columns：列集合（ColumnMeta），字段：
      - name：列名
      - nameCamel：列名小驼峰
      - type：数据库类型名
      - size：长度（可空）
      - nullable：是否可空
      - primaryKey：是否主键
      - javaType：映射后的 Java 类型（可能为全限定名）
      - comment：列注释
  - author：作者（可空）
  - date：生成日期（yyyy-MM-dd）
  - exclude：当前模板的排除列名列表（结合“一起生效”和各模板子页选择）
  - dtoExclude / voExclude：全局 DTO/VO 基础排除
  - useLombok：是否启用 Lombok
  - dtoImports / entityImports / voImports：需要导入的类型列表（去除 java.lang/kotlin 默认）
  - dtoPackage / voPackage / entityPackage / servicePackage / mapperPackage / controllerPackage / convertPackage：计算得到的各文件类型包名

- 枚举模板数据（templates/enums/*.ftl）
  - enumPackage：枚举类包名（默认为 `${packageName}.enums`）
  - enumName：枚举类名（如 `UserStatusEnum`）
  - tableName：当前表名
  - columnName：当前列名
  - enumItems：当列备注包含“代码-描述”对时抽取的列表，元素字段为 `code`、`label`、`name`
    - 示例循环：
      ```
      <#list enumItems as it>
      ${it.name}("${it.code}", "${it.label}")
      </#list>
      ```

### 模板开关变量 isEmptyImpl
- 含义：在模板选择列表的“排除字段”后有“空实现”复选框；勾选后该模板渲染时注入变量 `isEmptyImpl = true`
- 使用：
  ```
  <#if isEmptyImpl>
  // 生成空实现的分支
  </#if>
  ```
- 适用范围：所有参与生成的模板均可使用该变量；未勾选为 `false`

- 输出路径与文件名占位符
  - ${baseDir}：生成基准目录
  - ${projectBase}：项目根目录（部分场景）
  - ${packagePath}：包路径（点转斜杠）
  - ${packageName}：包名
  - ${entityName}：实体类名
  - ${tableName}：原始表名
  - 以上占位符在模板项的 outputPath 与文件名覆盖中均可使用

## 序列化支持
- serialVersionUID：提供给模板的长整型变量，用于实现可控序列化版本号
  - 生成规则：对字符串 "${packageName}.${entityName}.${templateName}" 进行 SHA-1，取前 8 字节为正数 long
  - 在预览与实际生成时一致，保证稳定
- 内置模板已默认支持
  - entity、dto、vo 模板已添加 implements java.io.Serializable，并声明字段
  - private static final long serialVersionUID = ${serialVersionUID}L;
- 自定义模板使用示例

```java
public class ${entityName} implements java.io.Serializable {
    private static final long serialVersionUID = ${serialVersionUID}L;
    // ...
}
```

## 常见问题
- 预览未出现按钮：确保文件位于 `my-easy-code/templates` 下且扩展名为 `.ftl`
- 包名不正确：检查保存路径指向 `src/main/java` 下的合规包路径
- 导入缺失或重复：模板顶部使用导入列表渲染；字段类型避免全限定类名
- 生成成功但无弹框：确认未出现错误弹框，成功弹框仅在无错误时显示

## 构建与安装
- 构建：`./gradlew.bat build -x test`
- 打包：`./gradlew.bat buildPlugin`  产出 `build/distributions/*.zip`
- 安装：主 IDEA `Settings -> Plugins -> Install Plugin from Disk...`

## 许可
- 本项目采用 MIT 开源许可证，详见 `LICENSE` 文件
