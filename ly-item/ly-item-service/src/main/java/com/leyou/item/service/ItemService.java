package com.leyou.item.service;

import com.leyou.item.pojo.Item;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * @author zhuqa
 * @projectName leyou
 * @description: TODO
 * @date 2019/10/17 22:41
 */
@Service
public class ItemService {
    public Item saveItem(Item item) {
        //商品新增
        int id = new Random().nextInt(100);
        item.setId(id);
        return item;
    }
}
