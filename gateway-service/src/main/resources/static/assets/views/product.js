import { api, session } from '../api.js';
import { esc, money, productImage, fmtDate, errorState, toast } from '../ui.js';

export async function renderProduct(root, params) {
    const id = params.id;
    root.innerHTML = `
        <div class="detail">
            <div class="detail__media skeleton"></div>
            <div class="detail__info">
                <div class="skeleton sk-line" style="width:70%;height:26px"></div>
                <div class="skeleton sk-line" style="width:38%;height:30px"></div>
                <div class="skeleton sk-line" style="width:100%;height:88px"></div>
            </div>
        </div>`;

    let product;
    try {
        product = await api.product(id);
    } catch (error) {
        root.innerHTML = `
            <div style="max-width:520px;margin:40px auto;display:grid;gap:14px">
                ${errorState(error.message)}
                <a class="btn btn-ghost" href="#/">返回商品列表</a>
            </div>`;
        return;
    }

    const soldOut = (product.stock ?? 0) <= 0 || product.status === 0;

    root.innerHTML = `
        <div class="detail">
            <div class="detail__media">
                <img src="${productImage(product, 900, 675)}" alt="${esc(product.title)}">
            </div>
            <div class="detail__info">
                <h1 class="detail__title">${esc(product.title)}</h1>
                <div style="display:flex;align-items:baseline;gap:12px">
                    <span class="detail__price price"><span class="price__unit">¥</span><span class="num">${money(product.price)}</span></span>
                    <span class="tag ${soldOut ? 'tag-done' : 'tag-ok'}">${soldOut ? '已下架' : '在售'}</span>
                </div>

                ${product.description
        ? `<p class="detail__desc">${esc(product.description)}</p>`
        : `<p class="detail__desc" style="color:var(--muted)">卖家没有填写描述，可以直接下单后联系卖家确认成色。</p>`}

                <dl class="detail__rows">
                    <div class="detail__row"><dt>剩余数量</dt><dd class="num">${product.stock ?? 0} 件</dd></div>
                    <div class="detail__row"><dt>卖家</dt><dd>用户 #${product.sellerId ?? '--'}</dd></div>
                    <div class="detail__row"><dt>发布时间</dt><dd class="num">${fmtDate(product.createTime)}</dd></div>
                </dl>

                <div class="detail__actions">
                    <button class="btn btn-primary" id="buy-btn" ${soldOut ? 'disabled' : ''}>
                        ${soldOut ? '已下架' : '立即下单'}
                    </button>
                    <a class="btn btn-ghost" href="#/">继续逛逛</a>
                </div>
                <p class="field-hint">下单后请与卖家约定校内面交时间，平台不代收货款。</p>
            </div>
        </div>
    `;

    root.querySelector('#buy-btn')?.addEventListener('click', async (event) => {
        if (!session.loggedIn) {
            toast('请先登录后再下单');
            window.location.hash = '#/login';
            return;
        }
        const btn = event.currentTarget;
        btn.disabled = true;
        btn.textContent = '提交中';
        try {
            const order = await api.createOrder(product.id, 1);
            toast('下单成功，请与卖家约定面交', 'ok');
            window.location.hash = `#/orders?highlight=${encodeURIComponent(order.orderNo)}`;
        } catch (error) {
            toast(error.message, 'err');
            btn.disabled = false;
            btn.textContent = '立即下单';
        }
    });
}
