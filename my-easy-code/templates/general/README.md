# templates/general

- 作用：存放普通代码模板（entity、service、serviceImpl、controller、mapper、mapperXml、dto、vo 等）
- 使用：将 .ftl 模板放入该目录，生成弹框的“模板选择”会自动加载
- 命名：建议与模板名称一致，例如 entity.ftl、service.ftl 等
- 变量：支持 ${baseDir}、${packagePath}、${packageName}、${entityName}、${tableName} 等
  详见项目根 README 的“模板支持的变量”