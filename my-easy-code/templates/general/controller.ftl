package ${filePackage};

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import ${servicePackage}.${entityName}Service;
import ${dtoPackage}.${entityName}DTO;
import ${voPackage}.${entityName}VO;

@RestController
@RequiredArgsConstructor
<#assign rawName = table.name>
<#assign prefix = stripPrefix!"" >
<#assign baseName = (rawName?starts_with(prefix))?then(rawName?substring(prefix?length), rawName)>
@RequestMapping("/api/${baseName?replace('_','/')}")
public class ${entityName}Controller {
    private final ${entityName}Service service;

<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
    @GetMapping("/{id}")
    public ${entityName}VO get(@PathVariable ${pkType} id) { return service.getById(id); }

    @GetMapping
    public List<${entityName}VO> list() { return service.list(); }

    @PostMapping
    public ${entityName}VO add(@RequestBody ${entityName}DTO body) { return service.add(body); }

    @PostMapping("/page")
    public IPage<${entityName}VO> page(@RequestBody PageDTO dto) { return service.page(dto); }

    @PutMapping
    public ${entityName}VO update(@RequestBody ${entityName}DTO body) { return service.update(body); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable ${pkType} id) { service.delete(id); }
}
