package ${filePackage};
<#if useLombok>
import lombok.Data;
</#if>
import java.io.Serializable;
import java.io.Serial;
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
</#if>
public class ${className} implements Serializable {
    @Serial
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
