# templates/enums

- 作用：存放枚举模板，供“枚举字段”面板选择生成枚举类
- 使用：将 .ftl 模板放入该目录，在“选择枚举模板”下拉中选择该模板
- 变量：enumPackage、enumName、tableName、columnName、columnComment、enumItems
- 输出：默认生成到 src/main/java/${packagePath}/enums，可在枚举字段行“保存路径”覆盖

## 基础枚举模板示例

```ftl
package ${enumPackage};

public enum ${enumName} {
    ;
}
```

## 使用 enumItems 循环生成枚举常量

当列备注中包含形如“ACTIVE-停用，INACTIVE-启用”的内容时，系统会抽取为 enumItems：

```ftl
<#list ${enumItems} as it>
${it.name}("${it.code}", "${it.label}")
</#list>
```