package com.leyou.item.service;

import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.item.mapper.CategoryMapper;
import com.leyou.item.pojo.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    public List<Category> queryCategoryListByPid(Long pid) {
        //查询条件，mapper会把对象中的非空属性作为查询条件
        Category t = new Category();
        t.setParentId(pid);
        List<Category> list = categoryMapper.select(t);
        if (CollectionUtils.isEmpty(list)) {
            throw new LyException(ExceptionEnum.CATEGORY_NOT_FOUND);
        }
        return list;
    }

    public List<Category> queryByIds(List<Long> ids) {
        List<Category> list = categoryMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(list)) {
            throw new LyException(ExceptionEnum.CATEGORY_NOT_FOUND);
        }
        return list;
    }

    /**
     * 根据ids查询名字
     * @param asList
     * @return
     */
    public List<String> queryNameByIds(List<Long> asList) {
        List<String> names = new ArrayList<>();
        if (asList != null && asList.size() !=0){
            for (Long id : asList) {
                names.add(this.categoryMapper.queryNameById(id));
            }
        }
        return names;
        //使用通用mapper接口中的SelectByIdListMapper接口查询
        //return this.categoryMapper.selectByIdList(asList).stream().map(Category::getName).collect(Collectors.toList());
    }

    /**
     * 根据cid3查询其所有层级分类
     * @param id
     * @return
     */
    public List<Category> queryAllCategoryLevelByCid3(Long id) {
        List<Category> categoryList = new ArrayList<>();
        Category category = this.categoryMapper.selectByPrimaryKey(id);
        while (category.getParentId() != 0){
            categoryList.add(category);
            category = this.categoryMapper.selectByPrimaryKey(category.getParentId());
        }
        categoryList.add(category);
        return categoryList;
    }
}
