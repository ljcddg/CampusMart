package com.campusmart.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流 key 解析器。
 *
 * <p>按客户端 IP 限流：网关的限流过滤器执行早于鉴权过滤器，此时还拿不到 X-User-Id。
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            InetSocketAddress address = exchange.getRequest().getRemoteAddress();
            String ip = address == null ? "unknown" : address.getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }
}
