package ${filePackage};
<#if useLombok>
import lombok.Data;
</#if>
<#if dtoImports?? && (dtoImports?size > 0)>
<#list dtoImports as im>
import ${im};
</#list>
</#if>
import javax.validation.constraints.Size;

<#if table.comment?? && table.comment?length gt 0>
/** ${table.comment} */
</#if>
<#if useLombok>
@Data
</#if>
public class ${entityName}DTO {
<#list table.columns as c>
<#if !(exclude?seq_contains(c.name))>
    <#if c.comment?? && c.comment?length gt 0>
    /** ${c.comment} */
    </#if>
    <#if !c.nullable>
        <#if c.javaType == 'String'>
    @NotBlank(<#if c.comment?? && c.comment?length gt 0>message="${c.comment}不能为空"<#else>message="${c.name}不能为空"</#if>)
        <#else>
    @NotNull(<#if c.comment?? && c.comment?length gt 0>message="${c.comment}不能为空"<#else>message="${c.name}不能为空"</#if>)
        </#if>
    </#if>
    <#if c.javaType == 'String' && c.size?? && c.size?number gt 0>
    @Size(max=${c.size}<#if c.comment?? && c.comment?length gt 0>, message="${c.comment}长度不能超过${c.size}"<#else>, message="${c.name}长度不能超过${c.size}"</#if>)
    </#if>
    private ${c.javaType?replace('^.*\\.', '', 'r')} ${c.nameCamel};
</#if>
</#list>
}
