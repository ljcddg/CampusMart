package com.campusmart.common.constant;

/**
 * 鉴权相关请求头常量。网关校验 JWT 后向下游注入 X-User-Id。
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** 前端传递 token 的请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 网关向下游透传的用户 ID */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关向下游透传的用户名 */
    public static final String HEADER_USERNAME = "X-Username";
}
