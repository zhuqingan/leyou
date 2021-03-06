package com.leyou.item.mapper;

import com.leyou.item.pojo.Category;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.additional.idlist.IdListMapper;
import tk.mybatis.mapper.common.Mapper;

public interface CategoryMapper extends Mapper<Category>, IdListMapper<Category, Long> {

    /**
     * 根据id查名字
     *
     * @param id
     * @return
     */
    @Select("SELECT name FROM tb_category WHERE id = #{id}")
    String queryNameById(Long id);
}
