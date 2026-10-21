/**
 * 通用 UI 工具：转义、格式化、Toast、骨架屏、空状态。
 */

export function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

export function money(value) {
    const n = Number(value ?? 0);
    return n.toFixed(2);
}

export function parseDate(value) {
    if (!value) return null;
    const normalized = typeof value === 'string' ? value.replace(' ', 'T') : value;
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
}

export function fmtDate(value) {
    const date = parseDate(value);
    if (!date) return '--';
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
        + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 倒计时拆分，done 表示已到期 */
export function countdown(target, now = Date.now()) {
    const date = target instanceof Date ? target : parseDate(target);
    if (!date) return { d: 0, h: 0, m: 0, s: 0, done: true };
    const diff = Math.max(0, date.getTime() - now);
    return {
        d: Math.floor(diff / 86400000),
        h: Math.floor(diff / 3600000) % 24,
        m: Math.floor(diff / 60000) % 60,
        s: Math.floor(diff / 1000) % 60,
        done: diff <= 0
    };
}

export function countdownHtml(target) {
    const t = countdown(target);
    const pad = (n) => String(n).padStart(2, '0');
    if (t.d > 0) {
        return `<span class="countdown__box">${t.d}</span><span class="countdown__sep">天</span>`
            + `<span class="countdown__box">${pad(t.h)}</span>`
            + `<span class="countdown__sep">:</span>`
            + `<span class="countdown__box">${pad(t.m)}</span>`;
    }
    return `<span class="countdown__box">${pad(t.h)}</span>`
        + `<span class="countdown__sep">:</span>`
        + `<span class="countdown__box">${pad(t.m)}</span>`
        + `<span class="countdown__sep">:</span>`
        + `<span class="countdown__box">${pad(t.s)}</span>`;
}

/** 商品图：无图时按 id 生成稳定的占位图 */
export function productImage(product, width = 640, height = 480) {
    if (product?.coverImage) return product.coverImage;
    const seed = `campusmart-${product?.id ?? 'x'}`;
    return `https://picsum.photos/seed/${encodeURIComponent(seed)}/${width}/${height}`;
}

export function toast(message, variant = '') {
    const host = document.getElementById('toast-host');
    if (!host) return;
    const el = document.createElement('div');
    el.className = `toast${variant ? ` toast--${variant}` : ''}`;
    el.textContent = message;
    host.appendChild(el);
    window.setTimeout(() => {
        el.style.transition = 'opacity .25s ease, transform .25s ease';
        el.style.opacity = '0';
        el.style.transform = 'translateY(8px)';
        window.setTimeout(() => el.remove(), 260);
    }, 2800);
}

export function skeletonGrid(count = 8) {
    return `<div class="product-grid">${Array.from({ length: count }).map(() => `
        <div class="sk-card">
            <div class="skeleton sk-card__media"></div>
            <div class="sk-card__body">
                <div class="skeleton sk-line"></div>
                <div class="skeleton sk-line sk-line--short"></div>
            </div>
        </div>`).join('')}</div>`;
}

export function emptyState({ title, text = '', action = '' }) {
    return `<div class="empty">
        <div class="empty__icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 7h18M3 12h18M3 17h10"/>
            </svg>
        </div>
        <p class="empty__title">${esc(title)}</p>
        ${text ? `<p class="empty__text">${esc(text)}</p>` : ''}
        ${action}
    </div>`;
}

export function errorState(message) {
    return `<div class="alert alert--error" role="alert">
        <span>${esc(message)}</span>
    </div>`;
}

export function pagerHtml(current, pages, total) {
    if (!pages || pages <= 1) return '';
    return `<div class="pager">
        <button class="pager__btn" data-page="${current - 1}" ${current <= 1 ? 'disabled' : ''}>上一页</button>
        <span class="pager__info">${current} / ${pages}　共 ${total} 件</span>
        <button class="pager__btn" data-page="${current + 1}" ${current >= pages ? 'disabled' : ''}>下一页</button>
    </div>`;
}

/** 订单状态标签 */
export function orderStatusTag(status) {
    if (status === 1) return '<span class="tag tag-ok">已成交</span>';
    if (status === 2) return '<span class="tag tag-done">处理失败</span>';
    return '<span class="tag tag-soon">处理中</span>';
}

/** 秒杀活动状态：live / soon / done */
export function activityPhase(activity) {
    const now = Date.now();
    const start = parseDate(activity?.startTime);
    const end = parseDate(activity?.endTime);
    if (start && now < start.getTime()) return 'soon';
    if (end && now > end.getTime()) return 'done';
    return 'live';
}

export function phaseTag(phase) {
    if (phase === 'soon') return '<span class="tag tag-soon">即将开始</span>';
    if (phase === 'done') return '<span class="tag tag-done">已结束</span>';
    return '<span class="tag tag-live">抢购中</span>';
}
