/**
 * HTTP 层：统一处理 JWT、业务码、401 跳转。
 * 所有请求走网关同源路径，不写死 host。
 */
const TOKEN_KEY = 'cm_token';
const USER_KEY = 'cm_user';

class ApiError extends Error {
    constructor(message, code) {
        super(message);
        this.name = 'ApiError';
        this.code = code;
    }
}

export const session = {
    get token() {
        return localStorage.getItem(TOKEN_KEY) || '';
    },
    get user() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
        } catch {
            return null;
        }
    },
    get loggedIn() {
        return Boolean(this.token);
    },
    save(token, user) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_KEY, JSON.stringify(user || {}));
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }
};

async function request(path, options = {}) {
    const { method = 'GET', body } = options;
    const headers = {};
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (session.token) {
        headers.Authorization = `Bearer ${session.token}`;
    }

    let response;
    try {
        response = await fetch(path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
    } catch {
        throw new ApiError('无法连接服务器，请确认网关已启动', -1);
    }

    if (response.status === 401) {
        session.clear();
        throw new ApiError('登录已过期，请重新登录', 401);
    }

    const text = await response.text();
    let payload = null;
    if (text) {
        try {
            payload = JSON.parse(text);
        } catch {
            payload = null;
        }
    }

    if (!response.ok) {
        throw new ApiError(payload?.message || `请求失败（HTTP ${response.status}）`,
            payload?.code ?? response.status);
    }
    if (payload && payload.code !== undefined && payload.code !== 200) {
        throw new ApiError(payload.message || '请求失败', payload.code);
    }
    return payload ? payload.data : null;
}

export const api = {
    /* ---------- 用户 ---------- */
    login: (username, password) =>
        request('/user/login', { method: 'POST', body: { username, password } }),

    register: (dto) =>
        request('/user/register', { method: 'POST', body: dto }),

    me: () => request('/user/info'),

    /* ---------- 商品 ---------- */
    products: (pageNum = 1, pageSize = 8) =>
        request(`/product/page?pageNum=${pageNum}&pageSize=${pageSize}`),

    product: (id) => request(`/product/${id}`),

    createProduct: (dto) => request('/product', { method: 'POST', body: dto }),

    offShelf: (id) => request(`/product/${id}`, { method: 'DELETE' }),

    /* ---------- 秒杀 ---------- */
    activities: (pageNum = 1, pageSize = 6) =>
        request(`/trade/seckill/activity/list?pageNum=${pageNum}&pageSize=${pageSize}`),

    seckill: (activityId) =>
        request(`/trade/seckill/${activityId}`, { method: 'POST' }),

    seckillStock: (activityId) => request(`/trade/seckill/stock/${activityId}`),

    /* ---------- 订单 ---------- */
    createOrder: (productId, quantity = 1) =>
        request('/trade/order', { method: 'POST', body: { productId, quantity } }),

    order: (orderNo) => request(`/trade/order/${orderNo}`),

    myOrders: (pageNum = 1, pageSize = 10) =>
        request(`/trade/order/page?pageNum=${pageNum}&pageSize=${pageSize}`)
};

export { ApiError };
