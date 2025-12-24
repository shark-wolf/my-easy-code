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

## 快速开始
- 启动沙箱：`./gradlew.bat runIde`
- 菜单使用：
  - `Tools -> Generate MyBatis Code`（从 `generator.yaml` 生成）
  - `Tools -> Generate From IDEA Database`（在 Database 视图选中表后生成）
- 弹框使用：
  - 选择表、填写包名与基准目录
  - 在模板选择区为每个模板设置“保存路径”（仅目录）
  - 在“排除字段”和“枚举字段”Tab分别按表勾选
  - 点击 `Create` 开始生成

## 路径与包名
- 保存路径仅覆盖目录：若输入路径末段包含`.`（看起来像文件），仅使用其父目录作为保存目录
- 包名计算：`package` 基于最终保存目录相对 `src/main/java` 的路径；模板内使用 `filePackage`
- 模板跨文件导入：通过 `dtoPackage/voPackage/entityPackage/servicePackage/mapperPackage/controllerPackage/convertPackage` 变量保证导入正确

## 常见问题
- 预览未出现按钮：确保文件位于 `my-easy-code/templates` 下且扩展名为 `.ftl`
- 包名不正确：检查保存路径指向 `src/main/java` 下的合规包路径
- 导入缺失或重复：模板顶部使用导入列表渲染；字段类型避免全限定类名
- 生成成功但无弹框：确认未出现错误弹框，成功弹框仅在无错误时显示

## 构建与安装
- 构建：`./gradlew.bat build -x test`
- 打包：`buildPlugin` 产出 `build/distributions/*.zip`
- 安装：主 IDEA `Settings -> Plugins -> Install Plugin from Disk...`

## 许可
- 本项目采用 MIT 开源许可证，详见 `LICENSE` 文件
