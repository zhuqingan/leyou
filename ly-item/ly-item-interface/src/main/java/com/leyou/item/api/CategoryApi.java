package com.leyou.item.api;

import com.leyou.item.pojo.Category;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
@RequestMapping("category")
public interface CategoryApi {

    @GetMapping("list/ids")
    List<Category> queryCateGoryByIds(@RequestParam("ids")List<Long> ids);

    /**
     * 根据id，查询分类名称
     * @param ids
     * @return
     */
    @GetMapping("names")
    List<String> queryNameByIds(@RequestParam("ids")List<Long> ids);
}
