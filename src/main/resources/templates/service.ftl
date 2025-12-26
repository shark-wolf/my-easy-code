package ${filePackage};

import java.util.List;
import ${dtoPackage}.${entityName}DTO;
import ${voPackage}.${entityName}VO;

public interface ${entityName}Service {
<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
    
    List<${entityName}VO> list();
    Boolean add(${entityName}DTO dto);
    Boolean update(${entityName}DTO dto);
    ${entityName}VO getById(${pkType} id);
    Boolean delete(${pkType} id);
}
