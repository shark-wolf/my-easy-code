package ${filePackage};
<#if useLombok>
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
</#if>
<#if voImports?? && (voImports?size > 0)>
<#list voImports as im>
import ${im};
</#list>
</#if>

<#if table.comment?? && table.comment?length gt 0>
/** ${table.comment} */
</#if>
<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
</#if>
public class ${entityName}VO implements java.io.Serializable {
    private static final long serialVersionUID = ${serialVersionUID}L;
<#list table.columns as c>
<#if !(exclude?seq_contains(c.name))>
    <#if c.comment?? && c.comment?length gt 0>
    /** ${c.comment} */
    </#if>
    private ${c.javaType?replace('^.*\\.', '', 'r')} ${c.nameCamel};
</#if>
</#list>
}
