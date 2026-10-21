package com.campusmart.gateway.filter;

import com.campusmart.common.constant.AuthConstants;
import com.campusmart.common.result.Result;
import com.campusmart.common.result.ResultCode;
import com.campusmart.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局 JWT 鉴权过滤器。
 *
 * <p>职责：校验 Authorization 头，解析出用户身份并以 X-User-Id 透传给下游服务。
 * 同时移除客户端可能伪造的同名请求头，避免绕过鉴权。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 无需登录即可访问的路径 */
    private static final List<String> WHITE_LIST = List.of(
            "/user/login",
            "/user/register",
            "/product/page",
            "/product/*",
            "/trade/seckill/activity/list",
            "/trade/seckill/stock/*"
    );

    /** 前端静态资源后缀，直接放行，不参与鉴权 */
    private static final List<String> STATIC_SUFFIX = List.of(
            ".html", ".css", ".js", ".mjs", ".ico", ".png", ".jpg", ".jpeg",
            ".svg", ".webp", ".woff2", ".json", ".map", ".txt"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isStaticResource(path) || isWhiteList(path)) {
            // 白名单也清掉伪造头，保证下游不会拿到不可信的身份信息
            return chain.filter(exchange.mutate().request(cleanHeaders(exchange.getRequest())).build());
        }

        String token = resolveToken(exchange.getRequest());
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange.getResponse(), "缺少登录凭证");
        }

        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            return unauthorized(exchange.getResponse(), "登录已过期，请重新登录");
        }

        Long userId = jwtUtil.getUserId(claims);
        String username = jwtUtil.getUsername(claims);
        if (userId == null) {
            return unauthorized(exchange.getResponse(), "登录凭证无效");
        }

        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(AuthConstants.HEADER_USER_ID);
                    headers.remove(AuthConstants.HEADER_USERNAME);
                    headers.set(AuthConstants.HEADER_USER_ID, String.valueOf(userId));
                    if (StringUtils.hasText(username)) {
                        headers.set(AuthConstants.HEADER_USERNAME, username);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    private boolean isStaticResource(String path) {
        if ("/".equals(path) || path.startsWith("/assets/")) {
            return true;
        }
        String lower = path.toLowerCase();
        return STATIC_SUFFIX.stream().anyMatch(lower::endsWith);
    }

    private boolean isWhiteList(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(AuthConstants.TOKEN_HEADER);
        if (!StringUtils.hasText(header)) {
            return null;
        }
        return header.startsWith(AuthConstants.TOKEN_PREFIX)
                ? header.substring(AuthConstants.TOKEN_PREFIX.length())
                : header;
    }

    private ServerHttpRequest cleanHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(AuthConstants.HEADER_USER_ID);
                    headers.remove(AuthConstants.HEADER_USERNAME);
                })
                .build();
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Result.fail(ResultCode.UNAUTHORIZED.getCode(), message));
        } catch (Exception e) {
            body = ("{\"code\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
