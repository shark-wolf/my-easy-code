package ${filePackage};

import ${servicePackage}.${serviceClassName};
import ${mapperPackage}.${mapperClassName};
import ${entityPackage}.${entityClassName};
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

<#if !isEmptyImpl>
import java.util.List;
import ${dtoPackage}.${dtoClassName};
import ${voPackage}.${voClassName};
import ${queryPackage}.${queryClassName};
import ${pageQueryPackage}.${pageQueryClassName};
import ${mapperPackage}.${mapperClassName};
 import ${updateDtoPackage}.${updateDtoClassName};
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.bean.BeanUtil;
import com.google.common.collect.Lists;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import cn.hutool.core.collection.CollectionUtil;
import org.springframework.util.Assert;
import org.springframework.transaction.annotation.Transactional;
</#if>


<#assign tableComment = "">
<#if table.comment?? && table.comment?length gt 0>
    <#assign tableComment = table.comment>
    <#if tableComment?ends_with("表")>
        <#assign tableComment = tableComment?substring(0, tableComment?length - 1)>
    </#if>
</#if>

/**
* @author: Neo
* @describe: ${tableComment} service实现
*/
@Service
public class ${className} extends ServiceImpl<${mapperClassName}, ${entityClassName}> implements ${serviceClassName} {

<#assign pkType="Long">
<#assign isOrderCreatTime = false>
<#assign isDeleted = false>

<#list table.columns as c>
<#if c.primaryKey>
<#assign pkType = c.javaType>
</#if>
<#if c.name == 'create_time'>
    <#assign isOrderCreatTime = true>
</#if>
<#if c.name == 'is_deleted'>
    <#assign isDeleted = true>
</#if>
</#list>

<#if !isEmptyImpl>
    @Override
    public IPage<${voClassName}> page(${pageQueryClassName} query){
        Page<${entityClassName}> poPage = new Page<>(query.getPageNum(), query.getPageSize());
        Wrapper<${entityClassName}> poWrapper = Wrappers.lambdaQuery(${entityClassName}.class)
         <#if !isOrderCreatTime && !isDeleted>
             ;
        <#elseif isOrderCreatTime && isDeleted>
            .orderByDesc(${entityClassName}::getCreateTime)
            .eq(${entityClassName}::getIsDeleted, false);
        <#elseif isOrderCreatTime && !isDeleted>
            .orderByDesc(${entityClassName}::getCreateTime);
        <#elseif isDeleted && !isOrderCreatTime>
            .eq(${entityClassName}::getIsDeleted, false);
        </#if>
        Page<${entityClassName}> page = this.page(poPage, poWrapper);
        return page.convert(this::copyToVO);
    }

    @Override
    public List<${voClassName}> list(${queryClassName} query){
        Wrapper<${entityClassName}> poWrapper = Wrappers.lambdaQuery(${entityClassName}.class)<#if !isOrderCreatTime && !isDeleted>;</#if>
        <#if !isOrderCreatTime && !isDeleted>
            ;
        <#elseif isOrderCreatTime && isDeleted>
            .orderByDesc(${entityClassName}::getCreateTime)
            .eq(${entityClassName}::getIsDeleted, false);
        <#elseif isOrderCreatTime && !isDeleted>
            .orderByDesc(${entityClassName}::getCreateTime);
        <#elseif isDeleted && !isOrderCreatTime>
            .eq(${entityClassName}::getIsDeleted, false);
        </#if>
        return copyVOList(this.list(poWrapper));
    }

    @Override
    public ${voClassName} getOneById(Long id){
        if (ObjUtil.isNull(id)) return null;
        return copyToVO(this.baseMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean add(${dtoClassName} dto){
        ${entityClassName} po = copyToPO(dto);
        return this.save(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean update(${updateDtoClassName} dto){
        Assert.isTrue(this.count(Wrappers.lambdaQuery(${entityClassName}.class).eq(${entityClassName}::getId, dto.getId())) > 0, "数据不存在");
        ${entityClassName} po = copyToPO(dto);
        return this.updateById(po);
    }

    private ${entityClassName} copyToPO(${dtoClassName} dto) {
        ${entityClassName} po = BeanUtil.copyProperties(dto, ${entityClassName}.class);
        return po;
    }

    private ${voClassName} copyToVO(${entityClassName} po) {
        if (ObjUtil.isNull(po)) return null;
        ${voClassName} vo = BeanUtil.copyProperties(po, ${voClassName}.class);

        return vo;
    }

    private List<${voClassName} > copyVOList(List<${entityClassName}> poList) {
        if (CollectionUtil.isEmpty(poList)) return null;
        List<${voClassName} > result = Lists.newArrayList();
        for (${entityClassName} po : poList) {
             result.add(copyToVO(po));
        }
        return result;
   }
</#if>

}