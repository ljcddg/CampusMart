package com.campusmart.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类，端口 7000。
 *
 * <p>注意：网关是 WebFlux 应用，不能引入 spring-boot-starter-web，
 * 因此只扫描 gateway 包与 common 的 JWT 工具，不加载基于 MVC 的全局异常处理器。
 */
@SpringBootApplication(scanBasePackages = {"com.campusmart.gateway", "com.campusmart.common.util"})
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
