package ${filePackage};

import org.springframework.stereotype.Service;
import java.util.List;
import ${mapperPackage}.${entityName}Mapper;
import ${entityPackage}.${entityName};
import ${servicePackage}.${entityName}Service;
import ${dtoPackage}.${entityName}DTO;
import ${voPackage}.${entityName}VO;

@Service
public class ${entityName}ServiceImpl implements ${entityName}Service {

<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
   

    @Override
    public List<${entityName}VO> list() {

        return null;
    }

    @Override
    public Boolean add(${entityName}DTO dto) {

        return null;
    }

    @Override
    public Boolean update(${entityName}DTO dto) {
   
        return null;
    }

     @Override
    public ${entityName}VO getById(${pkType} id) {
    
        return null;
    }

    @Override
    public Boolean delete(${pkType} id) { 
        
        return null;
    }
}
