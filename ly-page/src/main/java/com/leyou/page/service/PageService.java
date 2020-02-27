package com.leyou.page.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leyou.common.utils.JsonUtils;
import com.leyou.item.pojo.*;
import com.leyou.page.client.BrandClient;
import com.leyou.page.client.CategoryClient;
import com.leyou.page.client.GoodsClient;
import com.leyou.page.client.SpecificationClient;
import com.netflix.discovery.converters.Auto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PageService {
    private static final Logger logger = LoggerFactory.getLogger(PageService.class);

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private BrandClient brandClient;

    @Autowired
    private CategoryClient categoryClient;

    @Autowired
    private SpecificationClient specClient;

    @Autowired
    private TemplateEngine templateEngine;

    /*public Map<String, Object> loadModel(Long spuId) {
        Map<String,Object> model = new HashMap<>();
        //查询spu
        Spu spu = goodsClient.querySpuById(spuId);
        //查询skus
        List<Sku> skus = spu.getSkus();
        //查询详情
        SpuDetail detail = spu.getSpuDetail();
        Brand brand = brandClient.queryBrandById(spu.getBrandId());
        List<Category> categories = categoryClient.queryCateGoryByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        List<SpecGroup> specs = specClient.queryGroupByCid(spu.getCid3());
        model.put("spu",spu);
        model.put("skus",skus);
        model.put("detail",detail);
        model.put("brand",brand);
        model.put("categories",categories);
        model.put("specs",specs);
        return model;
    }*/

    public Map<String, Object> loadModel(Long spuId) throws InterruptedException, ExecutionException {

        final CountDownLatch countDownLatch = new CountDownLatch(2);
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        Spu spu = executorService.submit(() -> {
            countDownLatch.countDown();
            return this.goodsClient.querySpuById(spuId);
        }).get();

        Brand brand = executorService.submit(() -> {
            countDownLatch.countDown();
            return this.brandClient.queryBrandByIds(Collections.singletonList(spu.getBrandId())).get(0);
        }).get();

        countDownLatch.await();

        SpuDetail spuDetail = spu.getSpuDetail();
        List<Sku> skuList = spu.getSkus();
        List<Long> ids = new ArrayList<>();
        ids.add(spu.getCid1());
        ids.add(spu.getCid2());
        ids.add(spu.getCid3());

        List<Category> categories = categoryClient.queryCateGoryByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        /**
         * 对于规格属性的处理需要注意以下几点：
         *      1. 所有规格都保存为id和name形式
         *      2. 规格对应的值保存为id和value形式
         *      3. 都是map形式
         *      4. 将特有规格参数单独抽取
         */

        //获取所有规格参数，然后封装成为id和name形式的数据
        String allSpecJson = spuDetail.getSpecifications();
        List<Map<String,Object>> allSpecs = JsonUtils.nativeRead(allSpecJson, new TypeReference<List<Map<String, Object>>>() {
        });
        Map<Integer,String> specName = new HashMap<>();
        Map<Integer,Object> specValue = new HashMap<>();
        this.getAllSpecifications(allSpecs,specName,specValue);


        //获取特有规格参数
        String specTJson = spuDetail.getSpecTemplate();
        Map<String,String[]> specs = JsonUtils.nativeRead(specTJson, new TypeReference<Map<String, String[]>>() {
        });
        Map<Integer,String> specialParamName = new HashMap<>();
        Map<Integer,String[]> specialParamValue = new HashMap<>();
        this.getSpecialSpec(specs,specName,specValue,specialParamName,specialParamValue);

        //按照组构造规格参数
        List<Map<String,Object>> groups = this.getGroupsSpec(allSpecs,specName,specValue);



        Map<String,Object> map = new HashMap<>();
        map.put("spu",spu);
        map.put("spuDetail",spuDetail);
        map.put("skus",skuList);
        map.put("brand",brand);
        map.put("categories",categories);
        map.put("specName",specName);
        map.put("specValue",specValue);
        map.put("groups",groups);
        map.put("specialParamName",specialParamName);
        map.put("specialParamValue",specialParamValue);

        return map;
    }

    public void createHtml(Long spuId) throws ExecutionException, InterruptedException {
        //上下文
        Context context = new Context();
        context.setVariables(loadModel(spuId));
        //输出流
        File dest = new File("D:\\doc\\day13-html", spuId + ".html");
        try (PrintWriter writer = new PrintWriter(dest, "UTF-8")){
            templateEngine.process("item",context,writer);
        } catch (Exception e){
            logger.error("[静态页服务] 生成静态页异常",e);
        }


    }

    private List<Map<String, Object>> getGroupsSpec(List<Map<String, Object>> allSpecs, Map<Integer, String> specName, Map<Integer, Object> specValue) {
        List<Map<String, Object>> groups = new ArrayList<>();
        int i = 0;
        int j = 0;
        for (Map<String,Object> spec :allSpecs){
            List<Map<String, Object>> params = (List<Map<String, Object>>) spec.get("params");
            List<Map<String,Object>> temp = new ArrayList<>();
            for (Map<String,Object> param :params) {
                for (Map.Entry<Integer, String> entry : specName.entrySet()) {
                    if (entry.getValue().equals(param.get("k").toString())) {
                        String value = specValue.get(entry.getKey()) != null ? specValue.get(entry.getKey()).toString() : "无";
                        Map<String, Object> temp3 = new HashMap<>(16);
                        temp3.put("id", ++j);
                        temp3.put("name", entry.getValue());
                        temp3.put("value", value);
                        temp.add(temp3);
                    }
                }
            }
            Map<String,Object> temp2 = new HashMap<>(16);
            temp2.put("params",temp);
            temp2.put("id",++i);
            temp2.put("name",spec.get("group"));
            groups.add(temp2);
        }
        return groups;
    }

    private void getSpecialSpec(Map<String, String[]> specs, Map<Integer, String> specName, Map<Integer, Object> specValue, Map<Integer, String> specialParamName, Map<Integer, String[]> specialParamValue) {
        if (specs != null) {
            for (Map.Entry<String, String[]> entry : specs.entrySet()) {
                String key = entry.getKey();
                for (Map.Entry<Integer,String> e : specName.entrySet()) {
                    if (e.getValue().equals(key)){
                        specialParamName.put(e.getKey(),e.getValue());
                        //因为是放在数组里面，所以要先去除两个方括号，然后再以逗号分割成数组
                        String  s = specValue.get(e.getKey()).toString();
                        String result = StringUtils.substring(s,1,s.length()-1);
                        specialParamValue.put(e.getKey(), result.split(","));
                    }
                }
            }
        }
    }

    private void getAllSpecifications(List<Map<String, Object>> allSpecs, Map<Integer, String> specName, Map<Integer, Object> specValue) {
        String k = "k";
        String v = "v";
        String unit = "unit";
        String numerical = "numerical";
        String options ="options";
        int i = 0;
        if (allSpecs != null){
            for (Map<String,Object> s : allSpecs){
                List<Map<String, Object>> params = (List<Map<String, Object>>) s.get("params");
                for (Map<String,Object> param :params){
                    String result;
                    if (param.get(v) == null){
                        result = "无";
                    }else{
                        result = param.get(v).toString();
                    }
                    if (param.containsKey(numerical) && (boolean) param.get(numerical)) {
                        if (result.contains(".")){
                            Double d = Double.valueOf(result);
                            if (d.intValue() == d){
                                result = d.intValue()+"";
                            }
                        }
                        i++;
                        specName.put(i,param.get(k).toString());
                        specValue.put(i,result+param.get(unit).toString());
                    } else if (param.containsKey(options)){
                        i++;
                        specName.put(i,param.get(k).toString());
                        specValue.put(i,param.get(options));
                    }else {
                        i++;
                        specName.put(i,param.get(k).toString());
                        specValue.put(i,param.get(v));
                    }
                }
            }
        }
    }
}
