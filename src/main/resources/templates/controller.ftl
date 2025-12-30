package ${filePackage};

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import ${servicePackage}.${serviceClassName};
import ${dtoPackage}.${dtoClassName};
import ${voPackage}.${voClassName};

@RestController
@RequiredArgsConstructor
<#assign rawName = table.name>
<#assign prefix = stripPrefix!"" >
<#assign baseName = (rawName?starts_with(prefix))?then(rawName?substring(prefix?length), rawName)>
@RequestMapping("/api/${baseName?replace('_','/')}")
public class ${className} {
    private final ${serviceClassName} service;

<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
    @GetMapping("/{id}")
    public ${voClassName} get(@PathVariable ${pkType} id) { return service.getById(id); }

    @GetMapping
    public List<${voClassName}> list() { return service.list(); }

    @PostMapping
    public ${voClassName} add(@RequestBody ${dtoClassName} body) { return service.add(body); }

    @PostMapping("/page")
    public IPage<${voClassName}> page(@RequestBody PageDTO dto) { return service.page(dto); }

    @PutMapping
    public ${voClassName} update(@RequestBody ${dtoClassName} body) { return service.update(body); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable ${pkType} id) { service.delete(id); }
}
