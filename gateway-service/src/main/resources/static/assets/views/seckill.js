import { api, session } from '../api.js';
import {
    esc, money, productImage, countdown, countdownHtml, fmtDate,
    emptyState, errorState, pagerHtml, toast, activityPhase, phaseTag
} from '../ui.js';

const PAGE_SIZE = 6;
const sleep = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

export async function renderSeckill(root) {
    root.innerHTML = `
        <div class="section-head">
            <div>
                <h1 class="section-title">限时秒杀</h1>
                <p class="section-sub">每人每场限购一件，下单后订单由消息队列异步生成</p>
            </div>
            <a class="btn btn-ghost btn-sm" href="#/">回到全部商品</a>
        </div>
        <div id="sk-body"></div>
    `;

    const body = root.querySelector('#sk-body');
    let ticker = null;
    let disposed = false;

    function startTicker() {
        if (ticker) window.clearInterval(ticker);
        ticker = window.setInterval(() => {
            if (disposed) return;
            body.querySelectorAll('[data-deadline]').forEach((el) => {
                const deadline = Number(el.dataset.deadline);
                const parts = countdown(new Date(deadline));
                if (parts.done) {
                    el.innerHTML = '<span class="tag tag-done">已结束</span>';
                    return;
                }
                el.innerHTML = countdownHtml(new Date(deadline));
            });
        }, 1000);
    }

    async function load(pageNum = 1) {
        body.innerHTML = `<div class="seckill-list">${Array.from({ length: 3 }).map(() => `
            <div class="sk-card" style="display:grid;grid-template-columns:168px 1fr 178px;min-height:148px">
                <div class="skeleton"></div>
                <div class="sk-card__body">
                    <div class="skeleton sk-line" style="width:56%"></div>
                    <div class="skeleton sk-line sk-line--short"></div>
                </div>
                <div class="skeleton"></div>
            </div>`).join('')}</div>`;

        let page;
        try {
            page = await api.activities(pageNum, PAGE_SIZE);
        } catch (error) {
            body.innerHTML = errorState(error.message);
            return;
        }

        const records = page?.records ?? [];
        if (!records.length) {
            body.innerHTML = emptyState({
                title: '暂时没有进行中的秒杀',
                text: '活动开抢前 5 分钟会完成库存预热，稍后再来看看。',
                action: '<a class="btn btn-primary btn-sm" href="#/">浏览商品</a>'
            });
            return;
        }

        // 活动只存商品 ID，标题与图片需要回查商品服务
        const products = await Promise.all(
            records.map((activity) => api.product(activity.productId).catch(() => null))
        );

        body.innerHTML = `
            <div class="seckill-list">
                ${records.map((activity, index) => cardHtml(activity, products[index])).join('')}
            </div>
            ${pagerHtml(page.current, page.pages, page.total)}
        `;

        body.querySelectorAll('[data-seckill]').forEach((btn) => {
            btn.addEventListener('click', () => handleSeckill(Number(btn.dataset.seckill), btn));
        });
        body.querySelectorAll('[data-page]').forEach((btn) => {
            btn.addEventListener('click', () => load(Number(btn.dataset.page)));
        });

        startTicker();
    }

    function cardHtml(activity, product) {
        const phase = activityPhase(activity);
        const title = product?.title ?? `商品 #${activity.productId}`;
        const originPrice = product?.price ?? null;
        const remaining = activity.stock ?? 0;
        const deadline = phase === 'soon'
            ? new Date(activity.startTime).getTime()
            : new Date(activity.endTime).getTime();
        const disabled = phase !== 'live' || remaining <= 0;
        const label = phase === 'soon' ? '未开始' : (phase === 'done' ? '已结束' : (remaining <= 0 ? '已售罄' : '立即抢购'));

        return `<article class="seckill-card">
            <div class="seckill-card__media">
                <img src="${productImage(product ?? { id: activity.productId }, 400, 400)}"
                     alt="${esc(title)}" loading="lazy">
            </div>
            <div class="seckill-card__body">
                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                    ${phaseTag(phase)}
                    <span class="tag tag-plain">活动 #${activity.id}</span>
                </div>
                <h3 class="seckill-card__title">${esc(title)}</h3>
                <div class="seckill-card__prices">
                    <span class="price price--seckill"><span class="price__unit">¥</span><span class="num">${money(activity.seckillPrice)}</span></span>
                    ${originPrice ? `<span class="price--strike">原价 ¥${money(originPrice)}</span>` : ''}
                </div>
                <div class="seckill-card__foot">
                    <span>剩余 <strong class="num">${remaining}</strong> 件</span>
                    <span>${fmtDate(activity.startTime)} 开抢</span>
                </div>
            </div>
            <div class="seckill-card__action">
                <div class="countdown">
                    <span style="color:var(--muted);font-size:12px">${phase === 'soon' ? '距开始' : '距结束'}</span>
                    <span data-deadline="${deadline}" style="display:flex;align-items:center;gap:4px">${countdownHtml(new Date(deadline))}</span>
                </div>
                <button class="btn ${disabled ? 'btn-ghost' : 'btn-danger'} btn-block"
                        data-seckill="${activity.id}" ${disabled ? 'disabled' : ''}>${label}</button>
            </div>
        </article>`;
    }

    async function handleSeckill(activityId, btn) {
        if (!session.loggedIn) {
            toast('请先登录后再参与秒杀');
            window.location.hash = '#/login';
            return;
        }
        const original = btn.textContent;
        btn.disabled = true;
        btn.textContent = '排队中';

        try {
            const result = await api.seckill(activityId);
            toast('抢到了，订单正在生成', 'ok');
            await pollOrder(result.orderNo);
            await load(1);
        } catch (error) {
            toast(error.message, 'err');
            btn.disabled = false;
            btn.textContent = original;
            if (error.code === 3004 || error.code === 3005) {
                window.setTimeout(() => load(1), 600);
            }
        }
    }

    /** 秒杀是异步落单，前端轮询订单状态 */
    async function pollOrder(orderNo) {
        for (let i = 0; i < 10; i += 1) {
            await sleep(700);
            try {
                const order = await api.order(orderNo);
                if (order?.status === 1) {
                    toast('订单已生成，可在「我的订单」查看', 'ok');
                    return;
                }
                if (order?.status === 2) {
                    toast(`下单失败：${order.failReason || '库存已被抢完'}`, 'err');
                    return;
                }
            } catch {
                // 订单尚未落库时查询会 4041，继续轮询
            }
        }
        toast('订单仍在处理中，稍后到「我的订单」查看');
    }

    await load(1);

    return () => {
        disposed = true;
        if (ticker) window.clearInterval(ticker);
    };
}
