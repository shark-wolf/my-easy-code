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
    ${entityName}VO getById(${pkType} id);
    List<${entityName}VO> list();
    ${packageName}.common.page.PageVO<${entityName}VO> page(${packageName}.common.page.PageDTO dto);
    ${entityName}VO add(${entityName}DTO dto);
    ${entityName}VO update(${entityName}DTO dto);
    void delete(${pkType} id);
}
