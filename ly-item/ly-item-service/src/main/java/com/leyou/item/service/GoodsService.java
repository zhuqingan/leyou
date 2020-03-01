package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.mapper.SkuMapper;
import com.leyou.item.mapper.SpuDetailMapper;
import com.leyou.item.mapper.SpuMapper;
import com.leyou.item.mapper.StockMapper;
import com.leyou.item.pojo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GoodsService {

    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private SpuDetailMapper detailMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private AmqpTemplate amqpTemplate;


    public PageResult<Spu> querySpuByPage(Integer page, Integer rows, Boolean saleable, String key) {
        // 分页
        PageHelper.startPage(page, rows);
        // 过滤
        Example example = new Example(Spu.class);
        Example.Criteria criteria = example.createCriteria();
        // 搜索字段过滤
        if (StringUtils.isNotBlank(key)) {
            criteria.andLike("title", "%" + key + "%");
        }
        // 上下架过滤
        if (saleable != null) {
            criteria.andEqualTo("saleable", saleable);
        }
        // 默认排序
        example.setOrderByClause("last_update_time DESC");

        List<Spu> spus = spuMapper.selectByExample(example);

        loadCategoryAndBrandName(spus);

        //判断
        if (CollectionUtils.isEmpty(spus)) {
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }
        // 解析分页结果
        PageInfo<Spu> info = new PageInfo<>(spus);
        return new PageResult<>(info.getTotal(), spus);
    }

    private void loadCategoryAndBrandName(List<Spu> spus) {
        for (Spu spu : spus) {
            // 处理分类名称
            List<String> names = categoryService.queryByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()))
                    .stream().map(Category::getName).collect(Collectors.toList());
            // 处理品牌名称
            spu.setBname(brandService.queryById(spu.getBrandId()).getName());
        }
    }

    @Transactional
    public void saveGoods(SpuBo spuBo) {
        //新增spu
        spuBo.setId(null);
        spuBo.setCreateTime(new Date());
        spuBo.setLastUpdateTime(spuBo.getCreateTime());
        spuBo.setSaleable(true);
        spuBo.setValid(false);
        int count = spuMapper.insert(spuBo);
        if (count != 1) {
            throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
        }

        //新增detail
        SpuDetail detail = spuBo.getSpuDetail();
        detail.setSpuId(spuBo.getId());
        detailMapper.insert(detail);
        saveSkuAndStock(spuBo.getSkus(), spuBo.getId());

        // 发送mq消息
        amqpTemplate.convertAndSend("item.insert", spuBo.getId());

        /*//定义库存集合
        List<Stock> stockList = new ArrayList<>();
        //新增sku
        List<Sku> skus = spuBo.getSkus();
        for (Sku sku : skus) {
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            sku.setSpuId(spuBo.getId());

            count = skuMapper.insert(sku);

            if (count != 1){
                throw  new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
            }

            //新增库存
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            stockList.add(stock);
        }
        //批量新增库存
        stockMapper.insertList(stockList);*/
    }

    private void saveSkuAndStock(List<Sku> skus, Long spuId) {
        int count;
        //定义库存集合
        List<Stock> stockList = new ArrayList<>();
        for (Sku sku : skus) {
            if (!sku.getEnable()) {
                continue;
            }
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            sku.setSpuId(spuId);
            count = skuMapper.insert(sku);

            if (count != 1) {
                throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
            }

            //新增库存
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());
            stockList.add(stock);
        }
        //批量新增库存
        stockMapper.insertList(stockList);
    }

    public SpuDetail querySpuDetailById(Long id) {
        SpuDetail detail = detailMapper.selectByPrimaryKey(id);
        return detail;
    }

    public List<Sku> querySkuBySpuId(Long id) {
        // 查询sku
        Sku record = new Sku();
        record.setSpuId(id);
        List<Sku> skus = skuMapper.select(record);
        for (Sku sku : skus) {
            // 同时查询出库存
            sku.setStock(stockMapper.selectByPrimaryKey(sku.getId()).getStock());
        }
        return skus;
    }

    @Transactional
    public void update(SpuBo spuBo) {
        if (spuBo.getId() == null) {
            throw new LyException(ExceptionEnum.GOODS_ID_CANNOT_BE_NULL);
        }
        // 查询以前sku
        List<Sku> skus = querySkuBySpuId(spuBo.getId());
        // 如果以前存在，则删除
        if (!CollectionUtils.isEmpty(skus)) {
            List<Long> ids = skus.stream().map(s -> s.getId()).collect(Collectors.toList());
            // 删除以前库存
            Example example = new Example(Stock.class);
            example.createCriteria().andIn("skuId", ids);
            stockMapper.deleteByExample(example);

            // 删除以前的sku
            Sku record = new Sku();
            record.setSpuId(spuBo.getId());
            skuMapper.delete(record);
        }

        // 更新spu
        spuBo.setLastUpdateTime(new Date());
        spuBo.setCreateTime(null);
        spuBo.setValid(null);
        spuBo.setSaleable(null);

        int count = spuMapper.updateByPrimaryKeySelective(spuBo);
        if (count != 1) {
            throw new LyException(ExceptionEnum.GOODS_UPDATE_ERROR);
        }
        // 更新spu详情
        count = detailMapper.updateByPrimaryKeySelective(spuBo.getSpuDetail());
        if (count != 1) {
            throw new LyException(ExceptionEnum.GOODS_UPDATE_ERROR);
        }
        // 新增sku和库存
        saveSkuAndStock(spuBo.getSkus(), spuBo.getId());

        // 发送mq消息
        amqpTemplate.convertAndSend("item.update", spuBo.getId());
    }

    public Spu querySpuById(Long id) {
        // 查询Spu
        Spu spu = spuMapper.selectByPrimaryKey(id);
        if (spu == null) {
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }
        //查询sku
        List<Sku> skus = querySkuBySpuId(id);
        spu.setSkus(skus);
        //查询detail
        spu.setSpuDetail(querySpuDetailById(id));
        return spu;
    }
}
