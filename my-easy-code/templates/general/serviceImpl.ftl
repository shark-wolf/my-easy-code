package ${filePackage};

import org.springframework.stereotype.Service;
import java.util.List;
import ${mapperPackage}.${mapperClassName};
import ${entityPackage}.${entityClassName};
import ${servicePackage}.${serviceClassName};
import ${dtoPackage}.${dtoClassName};
import ${voPackage}.${voClassName};

@Service
public class ${className} implements ${serviceClassName} {

<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
   

    @Override
    public List<${voClassName}> list() {

        return null;
    }

    @Override
    public Boolean add(${dtoClassName} dto) {

        return null;
    }

    @Override
    public Boolean update(${dtoClassName} dto) {
   
        return null;
    }

     @Override
    public ${voClassName} getById(${pkType} id) {
    
        return null;
    }

    @Override
    public Boolean delete(${pkType} id) { 
        
        return null;
    }
}
