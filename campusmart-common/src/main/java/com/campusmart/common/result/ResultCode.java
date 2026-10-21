package com.campusmart.common.result;

import lombok.Getter;

/**
 * 业务状态码枚举。
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    USER_NOT_EXIST(1001, "用户不存在"),
    USER_ALREADY_EXIST(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),

    PRODUCT_NOT_EXIST(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    PRODUCT_STOCK_NOT_ENOUGH(2003, "商品库存不足"),
    PRODUCT_STOCK_CONFLICT(2004, "库存扣减冲突，请重试"),

    SECKILL_NOT_EXIST(3001, "秒杀活动不存在"),
    SECKILL_NOT_START(3002, "秒杀尚未开始"),
    SECKILL_ENDED(3003, "秒杀已结束"),
    SECKILL_SOLD_OUT(3004, "已售罄"),
    SECKILL_REPEAT(3005, "您已抢购过该商品，请勿重复参与"),
    SECKILL_SUBMIT_TOO_FAST(3006, "请勿重复提交"),

    ORDER_NOT_EXIST(4001, "订单不存在");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
