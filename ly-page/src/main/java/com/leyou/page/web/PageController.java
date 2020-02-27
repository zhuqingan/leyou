package com.leyou.page.web;

import com.leyou.page.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
public class PageController {

    @Autowired
    private PageService pageService;

    @GetMapping("item/{id}.html")
    public String toItemPage(@PathVariable("id") Long spuId, Model model) throws ExecutionException, InterruptedException {
        //查询模型数据
        Map<String,Object> attributies = pageService.loadModel(spuId);
        //准备模型数据
        model.addAllAttributes(attributies);
        //返回视图
        return "item";
    }
}
