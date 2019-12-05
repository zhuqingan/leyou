package com.leyou;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import tk.mybatis.spring.annotation.MapperScan;

/**
 * @author zhuqa
 * @projectName leyou
 * @description: TODO
 * @date 2019/10/16 23:07
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.leyou.item.mapper")
public class LyitemApplication {
    public static void main(String[] args) {
        SpringApplication.run(LyitemApplication.class, args);
    }
}
