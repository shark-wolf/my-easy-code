package ${filePackage};

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;
import ${mapperPackage}.${entityName}Mapper;
import ${entityPackage}.${entityName};
import ${servicePackage}.${entityName}Service;
import ${dtoPackage}.${entityName}DTO;
import ${voPackage}.${entityName}VO;
import ${convertPackage}.${entityName}Convert;

@Service
@RequiredArgsConstructor
public class ${entityName}ServiceImpl implements ${entityName}Service {
    private final ${entityName}Mapper mapper;
    private final ${entityName}Convert convert;

<#assign pkType="Long">
<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
</#list>
    @Override
    public ${entityName}VO getById(${pkType} id) {
        ${entityName} entity = mapper.selectById(id);
        return convert.toVo(entity);
    }

    @Override
    public List<${entityName}VO> list() {
        List<${entityName}> list = mapper.selectAll();
        return convert.toVoList(list);
    }

    @Override
    public ${packageName}.common.page.PageVO<${entityName}VO> page(${packageName}.common.page.PageDTO dto) {
        int size = dto.getSize();
        int page = dto.getPage();
        int offset = (page - 1) * size;
        List<${entityName}> list = mapper.selectPage(offset, size);
        long total = mapper.countAll();
        List<${entityName}VO> vos = convert.toVoList(list);
        ${packageName}.common.page.PageVO<${entityName}VO> vo = new ${packageName}.common.page.PageVO<>();
        vo.setRecords(vos);
        vo.setTotal(total);
        vo.setPage(page);
        vo.setSize(size);
        return vo;
    }

    @Override
    public ${entityName}VO add(${entityName}DTO dto) {
        ${entityName} entity = convert.toEntity(dto);
        mapper.insert(entity);
        return convert.toVo(entity);
    }

    @Override
    public ${entityName}VO update(${entityName}DTO dto) {
        ${entityName} entity = convert.toEntity(dto);
        mapper.updateById(entity);
        return convert.toVo(entity);
    }

    @Override
    public void delete(${pkType} id) { mapper.deleteById(id); }
}
