package ${filePackage};
<#if useLombok>
import lombok.Data;
import lombok.Builder;
</#if>
import java.io.Serializable;
import java.io.Serial;
<#if entityImports?? && (entityImports?size > 0)>
<#list entityImports as im>
import ${im};
</#list>
</#if>

<#if table.comment?? && table.comment?length gt 0>
/** ${table.comment} */
</#if>
<#if useLombok>
@Data
@Builder
</#if>
public class ${entityName} implements Serializable {
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

<#if !useLombok>
<#list table.columns as c>
<#if !(exclude?seq_contains(c.name))>
    public ${c.javaType?replace('^.*\\.', '', 'r')} get${c.nameCamel?cap_first}() { return ${c.nameCamel}; }
    public void set${c.nameCamel?cap_first}(${c.javaType?replace('^.*\\.', '', 'r')} ${c.nameCamel}) { this.${c.nameCamel} = ${c.nameCamel}; }
</#if>
</#list>
</#if>
}
