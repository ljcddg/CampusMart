import { api, session } from '../api.js';
import { esc, money, productImage, toast } from '../ui.js';

export async function renderPublish(root) {
    if (!session.loggedIn) {
        root.innerHTML = `
            <div class="auth-wrap">
                <div class="auth-card" style="text-align:center">
                    <h1 class="auth-title">发布闲置需要先登录</h1>
                    <p class="auth-sub">登录后可以把教材、数码、生活用品挂到平台上</p>
                    <a class="btn btn-primary btn-block" href="#/login">去登录</a>
                </div>
            </div>`;
        return;
    }

    root.innerHTML = `
        <div class="section-head">
            <div>
                <h1 class="section-title">发布闲置</h1>
                <p class="section-sub">照片与描述越具体，成交越快</p>
            </div>
            <a class="btn btn-ghost btn-sm" href="#/">回到商品列表</a>
        </div>

        <div class="publish-grid">
            <form class="publish-panel" id="publish-form" novalidate>
                <div class="field">
                    <label for="title">标题</label>
                    <input class="input" id="title" name="title" maxlength="200"
                           placeholder="例如：高等数学第七版上册，九成新" autocomplete="off">
                </div>

                <div class="form-row">
                    <div class="field">
                        <label for="price">价格（元）</label>
                        <input class="input" id="price" name="price" type="number"
                               min="0.01" step="0.01" placeholder="25.00" inputmode="decimal">
                    </div>
                    <div class="field">
                        <label for="stock">库存（件）</label>
                        <input class="input" id="stock" name="stock" type="number"
                               min="0" step="1" value="1" inputmode="numeric">
                    </div>
                </div>

                <div class="field">
                    <label for="description">描述</label>
                    <textarea class="textarea" id="description" name="description" maxlength="2000"
                              placeholder="成色、购买时间、有无笔记或配件"></textarea>
                    <span class="field-hint">填写成色与瑕疵，能减少一半的追问</span>
                </div>

                <div class="field">
                    <label for="coverImage">封面图链接（选填）</label>
                    <input class="input" id="coverImage" name="coverImage" type="url"
                           placeholder="https://..." autocomplete="off">
                    <span class="field-hint">留空时展示一张默认占位图</span>
                </div>

                <div id="form-error" class="field-error" role="alert" hidden></div>

                <button class="btn btn-primary btn-block" type="submit" id="submit-btn">确认发布</button>
            </form>

            <aside class="preview-card">
                <span class="preview-label">卡片预览</span>
                <article class="product-card">
                    <div class="product-card__media">
                        <img id="preview-img" src="${productImage({ id: 'new' })}" alt="预览">
                    </div>
                    <div class="product-card__body">
                        <h3 class="product-card__title" id="preview-title">商品标题</h3>
                        <div class="product-card__meta">
                            <span class="price"><span class="price__unit">¥</span><span class="num" id="preview-price">0.00</span></span>
                            <span class="stock-note" id="preview-stock">剩 1 件</span>
                        </div>
                    </div>
                </article>
            </aside>
        </div>
    `;

    const form = root.querySelector('#publish-form');
    const titleEl = root.querySelector('#title');
    const priceEl = root.querySelector('#price');
    const stockEl = root.querySelector('#stock');
    const coverEl = root.querySelector('#coverImage');
    const errorEl = root.querySelector('#form-error');

    const syncPreview = () => {
        root.querySelector('#preview-title').textContent = titleEl.value.trim() || '商品标题';
        root.querySelector('#preview-price').textContent = money(priceEl.value || 0);
        root.querySelector('#preview-stock').textContent = `剩 ${stockEl.value || 0} 件`;
        const cover = coverEl.value.trim();
        root.querySelector('#preview-img').src = cover || productImage({ id: 'new' });
    };

    [titleEl, priceEl, stockEl, coverEl].forEach((el) => {
        el.addEventListener('input', syncPreview);
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorEl.hidden = true;

        const title = titleEl.value.trim();
        const price = Number(priceEl.value);
        const stock = Number(stockEl.value);

        if (!title) {
            showError('请填写商品标题');
            return;
        }
        if (!Number.isFinite(price) || price <= 0) {
            showError('价格需要大于 0');
            return;
        }
        if (!Number.isInteger(stock) || stock < 0) {
            showError('库存需要是非负整数');
            return;
        }

        const btn = root.querySelector('#submit-btn');
        btn.disabled = true;
        btn.textContent = '发布中';

        try {
            const id = await api.createProduct({
                title,
                price,
                stock,
                description: root.querySelector('#description').value.trim(),
                coverImage: coverEl.value.trim() || null
            });
            toast('发布成功', 'ok');
            window.location.hash = `#/product/${id}`;
        } catch (error) {
            showError(error.message);
            btn.disabled = false;
            btn.textContent = '确认发布';
        }
    });

    function showError(message) {
        errorEl.textContent = message;
        errorEl.hidden = false;
    }

    syncPreview();
}
