package com.leyou.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.JsonUtils;
import com.leyou.common.vo.PageResult;
import com.leyou.item.pojo.*;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.repository.GoodsRepository;
import com.leyou.vo.SearchResult;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;

@Service
public class SearchService {

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private GoodsClient goodsClient;

    private ObjectMapper mapper = new ObjectMapper();

    public Goods buildGoods(Spu spu) throws IOException {

        Goods goods = new Goods();

        //1.查询商品分类名称
        List<String> names = this.categoryClient.queryNameByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        // 查询sku
        List<Sku> skus = goodsClient.querySkuBySpuId(spu.getId());
        if (CollectionUtils.isEmpty(skus)) {
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }
        //3.查询详情
        SpuDetail spuDetail = goodsClient.querySpuDetailById(spu.getId());
        // 查询品牌
        Brand brand = brandClient.queryBrandById(spu.getBrandId());
        if (brand == null) {
            throw new LyException(ExceptionEnum.BRAND_NOT_FOUND);
        }
        //4.处理sku,仅封装id，价格、标题、图片、并获得价格集合
        List<Long> prices = new ArrayList<>();
        List<Map<String, Object>> skuLists = new ArrayList<>();
        skus.forEach(sku -> {
            prices.add(sku.getPrice());
            Map<String, Object> skuMap = new HashMap<>();
            skuMap.put("id", sku.getId());
            skuMap.put("title", sku.getTitle());
            skuMap.put("price", sku.getPrice());
            //取第一张图片
            skuMap.put("image", StringUtils.substringBefore(sku.getImages(), ","));
        });

        //提取公共属性
        List<Map<String, Object>> genericSpecs = mapper.readValue(spuDetail.getSpecifications(), new TypeReference<List<Map<String, Object>>>() {
        });

        //过滤规格模板，把所有可搜索的信息保存到Map中
        Map<String, Object> specMap = new HashMap<>();

        String searchable = "searchable";
        String v = "v";
        String k = "k";
        String options = "options";

        genericSpecs.forEach(m -> {
            List<Map<String, Object>> params = (List<Map<String, Object>>) m.get("params");
            params.forEach(spe -> {
                if ((boolean) spe.get(searchable)) {
                    if (spe.get(v) != null) {
                        specMap.put(spe.get(k).toString(), spe.get(v));
                    } else if (spe.get(options) != null) {
                        specMap.put(spe.get(k).toString(), spe.get(options));
                    }
                }
            });
        });

        // 搜索字段
        String all = spu.getTitle() + StringUtils.join(names, " ") + brand.getName();

        //构建goods对象
        goods.setId(spu.getId());
        goods.setBrandId(spu.getBrandId());
        goods.setCid1(spu.getCid1());
        goods.setCid2(spu.getCid2());
        goods.setCid3(spu.getCid3());
        goods.setCreateTime(spu.getCreateTime());
        goods.setAll(all);
        goods.setPrice(prices);
        goods.setSkus(JsonUtils.serialize(skus));
        goods.setSpecs(specMap);
        goods.setSubTitle(spu.getSubTitle());
        return goods;
    }

    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if (segs.length == 2) {
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if (val >= begin && val < end) {
                if (segs.length == 1) {
                    result = segs[0] + p.getUnit() + "以上";
                } else if (begin == 0) {
                    result = segs[1] + p.getUnit() + "以下";
                } else {
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }

    public SearchResult<Goods> search(SearchRequest searchRequest) {
        String key = searchRequest.getKey();

        /**
         * 判断是否有搜索条件，如果没有，直接返回null。不允许搜索全部商品
         */
        if (StringUtils.isBlank(key)) {
            return null;
        }
        //构建查询条件
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //1.1.对关键字进行全文检索查询
        queryBuilder.withQuery(QueryBuilders.matchQuery("all", key).operator(Operator.AND));
        //1.2.通过sourceFilter设置返回的结果字段，只需要id,skus,subTitle
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id", "skus", "subTitle"}, null));
        //1.3.分页和排序
        searchWithPageAndSort(queryBuilder, searchRequest);

        //1.4聚合
        //商品分类聚合名称
        String categoryAggName = "category";
        //品牌聚合名称
        String brandAggName = "brand";
        //1.4.1 对商品分类进行聚合
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        //1.4.2 对品牌进行聚合
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));
        //2. 查询、获取结果
        AggregatedPage<Goods> pageInfo = (AggregatedPage<Goods>) this.goodsRepository.search(queryBuilder.build());
        //3. 解析查询结果
        //3.1 分页信息
        Long total = pageInfo.getTotalElements();
        int totalPage = pageInfo.getTotalPages();
        //3.2 商品分类聚合结果
        List<Category> categories = getCategoryAggResult(pageInfo.getAggregation(categoryAggName));
        //3.3 品牌的聚合结果
        List<Brand> brands = getBrandAggResult(pageInfo.getAggregation(brandAggName));
        //3.封装结果，返回
        return new SearchResult<>(total, (long) totalPage, pageInfo.getContent(), categories, brands);
    }

    /**
     * 构建基本查询条件
     *
     * @param queryBuilder
     * @param request
     */
    private void searchWithPageAndSort(NativeSearchQueryBuilder queryBuilder, SearchRequest request) {
        // 准备分页参数
        int page = request.getPage();
        int size = request.getDefaultSize();

        // 1、分页
        queryBuilder.withPageable(PageRequest.of(page - 1, size));
        // 2、排序
        String sortBy = request.getSortBy();
        Boolean desc = request.getDescending();
        if (StringUtils.isNotBlank(sortBy)) {
            queryBuilder.withSort(SortBuilders.fieldSort(sortBy).order(desc ? SortOrder.DESC : SortOrder.DESC));
        }
    }

    private List<Category> getCategoryAggResult(Aggregation aggregation) {
        LongTerms brandAgg = (LongTerms) aggregation;
        List<Long> cids = new ArrayList<>();
        for (LongTerms.Bucket bucket : brandAgg.getBuckets()) {
            cids.add(bucket.getKeyAsNumber().longValue());
        }
        // 根据品牌id查询品牌
        return this.categoryClient.queryCateGoryByIds(cids);
    }

    private List<Brand> getBrandAggResult(Aggregation aggregation) {
        LongTerms brandAgg = (LongTerms) aggregation;
        List<Long> bids = new ArrayList<>();
        for (LongTerms.Bucket bucket : brandAgg.getBuckets()) {
            bids.add(bucket.getKeyAsNumber().longValue());
        }
        // 根据品牌id查询品牌
        return this.brandClient.queryBrandByIds(bids);
    }

    public void creatOrUpdateIndex(Long spuId) {
        //查询spu
        Spu spu = goodsClient.querySpuById(spuId);
        // 构建goods
        Goods goods = null;
        try {
            goods = buildGoods(spu);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 存入索引库
        goodsRepository.save(goods);

    }

    public void deleteIndex(Long spuId) {
        goodsRepository.deleteById(spuId);
    }
}
