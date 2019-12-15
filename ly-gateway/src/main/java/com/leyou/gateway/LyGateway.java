package com.leyou.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;

/**
 * @author zhuqa
 * @projectName leyou
 * @description: TODO
 * @date 2019/10/16 22:04
 */
@EnableZuulProxy
@SpringCloudApplication
public class LyGateway {
    public static void main(String[] args) {
        SpringApplication.run(LyGateway.class, args);
    }
}
