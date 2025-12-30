package ${filePackage};

import java.util.List;
import ${dtoPackage}.${dtoClassName};
import ${voPackage}.${voClassName};

public interface ${className} {
<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
    
    List<${voClassName}> list();
    Boolean add(${dtoClassName} dto);
    Boolean update(${dtoClassName} dto);
    ${voClassName} getById(${pkType} id);
    Boolean delete(${pkType} id);
}
