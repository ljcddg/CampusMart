import { api, session } from '../api.js';
import { esc, money, fmtDate, emptyState, errorState, pagerHtml, orderStatusTag } from '../ui.js';

const PAGE_SIZE = 10;

export async function renderOrders(root, params = {}) {
    if (!session.loggedIn) {
        root.innerHTML = `
            <div class="auth-wrap">
                <div class="auth-card" style="text-align:center">
                    <h1 class="auth-title">登录后查看订单</h1>
                    <p class="auth-sub">秒杀下单是异步处理，订单生成进度会显示在这里</p>
                    <a class="btn btn-primary btn-block" href="#/login">去登录</a>
                </div>
            </div>`;
        return;
    }

    root.innerHTML = `
        <div class="section-head">
            <div>
                <h1 class="section-title">我的订单</h1>
                <p class="section-sub">处理中的订单会在几秒后自动刷新</p>
            </div>
            <a class="btn btn-ghost btn-sm" href="#/">继续逛逛</a>
        </div>
        <div id="order-body"></div>
    `;

    const body = root.querySelector('#order-body');
    let refreshTimer = null;
    let disposed = false;

    async function load(pageNum = 1) {
        if (refreshTimer) {
            window.clearTimeout(refreshTimer);
            refreshTimer = null;
        }
        body.innerHTML = `<div class="order-list">${Array.from({ length: 4 }).map(() => `
            <div class="order-row">
                <div class="order-row__main">
                    <div class="skeleton sk-line" style="width:44%"></div>
                    <div class="skeleton sk-line sk-line--short"></div>
                </div>
                <div class="skeleton" style="width:64px;height:22px;border-radius:999px"></div>
            </div>`).join('')}</div>`;

        let page;
        try {
            page = await api.myOrders(pageNum, PAGE_SIZE);
        } catch (error) {
            body.innerHTML = errorState(error.message);
            return;
        }

        const records = page?.records ?? [];
        if (!records.length) {
            body.innerHTML = emptyState({
                title: '还没有订单',
                text: '去商品列表看看，或者参加一场秒杀。',
                action: '<a class="btn btn-primary btn-sm" href="#/">浏览商品</a>'
            });
            return;
        }

        body.innerHTML = `
            <div class="order-list">
                ${records.map((order) => rowHtml(order, params.highlight)).join('')}
            </div>
            ${pagerHtml(page.current, page.pages, page.total)}
        `;

        body.querySelectorAll('[data-page]').forEach((btn) => {
            btn.addEventListener('click', () => load(Number(btn.dataset.page)));
        });

        if (params.highlight) {
            const target = body.querySelector(`[data-order-no="${CSS.escape(params.highlight)}"]`);
            if (target) {
                target.style.borderColor = 'var(--accent)';
                target.style.boxShadow = '0 0 0 3px rgba(22,98,78,.1)';
                target.scrollIntoView({ block: 'center', behavior: 'smooth' });
            }
        }

        // 秒杀订单由 MQ 异步生成，存在处理中的订单时自动刷新一次
        if (records.some((order) => order.status === 0) && !disposed) {
            refreshTimer = window.setTimeout(() => load(pageNum), 2600);
        }
    }

    function rowHtml(order, highlight) {
        const isSeckill = order.activityId !== null && order.activityId !== undefined;
        return `<article class="order-row" data-order-no="${esc(order.orderNo)}">
            <div class="order-row__main">
                <div class="order-row__title">
                    ${orderStatusTag(order.status)}
                    <span>${isSeckill ? `秒杀商品 #${order.productId}` : `商品 #${order.productId}`}</span>
                    ${isSeckill ? `<span class="tag tag-plain">活动 #${order.activityId}</span>` : ''}
                </div>
                <div class="order-row__no">订单号 ${esc(order.orderNo)}</div>
                ${order.failReason ? `<div class="field-error">${esc(order.failReason)}</div>` : ''}
                <div class="order-row__no">${fmtDate(order.createTime)}</div>
            </div>
            <div class="order-row__side">
                <span class="price"><span class="price__unit">¥</span><span class="num">${money(order.amount)}</span></span>
                ${highlight === order.orderNo ? '<span class="tag tag-ok">刚刚创建</span>' : ''}
            </div>
        </article>`;
    }

    await load(1);

    return () => {
        disposed = true;
        if (refreshTimer) window.clearTimeout(refreshTimer);
    };
}
