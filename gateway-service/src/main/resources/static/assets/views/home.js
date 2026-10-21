import { api } from '../api.js';
import {
    esc, money, productImage, skeletonGrid, emptyState, errorState, pagerHtml, toast
} from '../ui.js';

export async function renderHome(root) {
    root.innerHTML = `
        <section class="hero">
            <div class="hero__body">
                <span class="hero__eyebrow">校园二手 · 同校面交</span>
                <h1 class="hero__title">学期末清仓，教材和数码都在这儿</h1>
                <p class="hero__sub">毕业季的闲置交给下一位同学。看上直接下单，约在教学楼门口见面。</p>
                <div class="hero__actions">
                    <button class="btn btn-primary" data-scroll="list">浏览商品</button>
                    <a class="btn btn-ghost" href="#/seckill">今日秒杀</a>
                </div>
            </div>
            <div class="hero__media">
                <img src="https://picsum.photos/seed/campusmart-market-books/900/620"
                     alt="校园二手市集" loading="eager">
                <div class="hero__stat">
                    <span class="hero__stat-label">在售商品</span>
                    <span class="hero__stat-value num" id="hero-count">--</span>
                </div>
            </div>
        </section>

        <section id="list">
            <div class="section-head">
                <h2 class="section-title">最新上架</h2>
                <span class="section-sub">按下架时间与发布时间排序</span>
            </div>
            <div id="list-body">${skeletonGrid(8)}</div>
        </section>
    `;

    root.querySelector('[data-scroll="list"]')?.addEventListener('click', () => {
        document.getElementById('list')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });

    await loadProducts(root, 1, 8);

    const countEl = document.getElementById('hero-count');
    if (countEl) {
        api.products(1, 1)
            .then((page) => {
                countEl.textContent = page?.total ?? 0;
            })
            .catch(() => {
                countEl.textContent = '--';
            });
    }
}

async function loadProducts(root, pageNum, pageSize) {
    const body = root.querySelector('#list-body');
    if (!body) return;
    body.innerHTML = skeletonGrid(pageSize);

    try {
        const page = await api.products(pageNum, pageSize);
        const records = page?.records ?? [];

        if (!records.length) {
            body.innerHTML = emptyState({
                title: '还没有商品上架',
                text: '成为第一个发布闲置的同学，教材、数码、生活用品都可以。',
                action: '<a class="btn btn-primary btn-sm" href="#/publish">去发布</a>'
            });
            return;
        }

        body.innerHTML = `
            <div class="product-grid">
                ${records.map(productCard).join('')}
            </div>
            ${pagerHtml(page.current, page.pages, page.total)}
        `;

        body.querySelectorAll('.product-card').forEach((el) => {
            el.addEventListener('click', () => {
                window.location.hash = `#/product/${el.dataset.id}`;
            });
        });

        body.querySelectorAll('[data-page]').forEach((btn) => {
            btn.addEventListener('click', () => {
                const target = Number(btn.dataset.page);
                if (target >= 1 && target <= (page.pages || 1)) {
                    loadProducts(root, target, pageSize);
                    document.getElementById('list')?.scrollIntoView({ block: 'start' });
                }
            });
        });
    } catch (error) {
        body.innerHTML = errorState(error.message);
        toast(error.message, 'err');
    }
}

function productCard(product) {
    const soldOut = (product.stock ?? 0) <= 0 || product.status === 0;
    return `<article class="product-card" data-id="${product.id}" role="link" tabindex="0">
        <div class="product-card__media">
            <img src="${productImage(product)}" alt="${esc(product.title)}" loading="lazy">
        </div>
        <div class="product-card__body">
            <h3 class="product-card__title">${esc(product.title)}</h3>
            <div class="product-card__meta">
                <span class="price"><span class="price__unit">¥</span><span class="num">${money(product.price)}</span></span>
                <span class="stock-note">${soldOut ? '已下架' : `剩 ${product.stock} 件`}</span>
            </div>
        </div>
    </article>`;
}
