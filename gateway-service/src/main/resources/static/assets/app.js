import { session } from './api.js';
import { toast } from './ui.js';
import { renderHome } from './views/home.js';
import { renderProduct } from './views/product.js';
import { renderSeckill } from './views/seckill.js';
import { renderPublish } from './views/publish.js';
import { renderOrders } from './views/orders.js';
import { renderAuth } from './views/auth.js';

const ROUTES = [
    { path: '/', view: renderHome },
    { path: '/seckill', view: renderSeckill },
    { path: '/product/:id', view: renderProduct },
    { path: '/publish', view: renderPublish },
    { path: '/orders', view: renderOrders },
    { path: '/login', view: renderAuth }
];

const NAV = [
    { href: '#/', label: '商品', match: (p) => p === '/' || p.startsWith('/product') },
    { href: '#/seckill', label: '秒杀', match: (p) => p.startsWith('/seckill') },
    { href: '#/publish', label: '发布', match: (p) => p.startsWith('/publish') },
    { href: '#/orders', label: '我的订单', match: (p) => p.startsWith('/orders') }
];

const app = document.getElementById('app');
let cleanupCurrentView = null;

function parseHash() {
    const raw = window.location.hash.replace(/^#/, '') || '/';
    const [path, search] = raw.split('?');
    return { path: path || '/', query: new URLSearchParams(search || '') };
}

function matchRoute(path) {
    for (const route of ROUTES) {
        const routeSegments = route.path.split('/').filter(Boolean);
        const pathSegments = path.split('/').filter(Boolean);
        if (routeSegments.length !== pathSegments.length) continue;

        const params = {};
        let matched = true;
        for (let i = 0; i < routeSegments.length; i += 1) {
            const rs = routeSegments[i];
            if (rs.startsWith(':')) {
                params[rs.slice(1)] = decodeURIComponent(pathSegments[i]);
            } else if (rs !== pathSegments[i]) {
                matched = false;
                break;
            }
        }
        if (matched) return { route, params };
    }
    return null;
}

function headerHtml(path) {
    const user = session.user;
    const navLinks = NAV.map((item) => {
        const current = item.match(path);
        return `<a class="nav-link" href="${item.href}" ${current ? 'aria-current="page"' : ''}>${item.label}</a>`;
    }).join('');

    const actions = session.loggedIn
        ? `<a class="user-menu" href="#/orders" title="查看我的订单">
               <span class="avatar">${escapeInitial(user?.nickname || user?.username)}</span>
               <span class="user-menu__name">${escapeText(user?.nickname || user?.username || '同学')}</span>
           </a>
           <button class="btn btn-ghost btn-sm" id="logout-btn">退出</button>`
        : `<a class="btn btn-primary btn-sm" href="#/login">登录</a>`;

    return `<header class="site-header">
        <div class="site-header__inner">
            <a class="brand" href="#/">
                <span class="brand-mark" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2"
                         stroke-linecap="round" stroke-linejoin="round">
                        <path d="M4 17 12 5l8 12H4z"/>
                    </svg>
                </span>
                校淘
            </a>
            <nav class="nav">${navLinks}</nav>
            <div class="header-actions">${actions}</div>
        </div>
    </header>`;
}

function escapeText(value) {
    return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

function escapeInitial(value) {
    const text = String(value ?? '').trim();
    return escapeText(text ? text.slice(0, 1).toUpperCase() : '?');
}

async function render() {
    if (typeof cleanupCurrentView === 'function') {
        try {
            cleanupCurrentView();
        } catch {
            /* 视图清理失败不影响路由切换 */
        }
        cleanupCurrentView = null;
    }

    const { path, query } = parseHash();
    const matched = matchRoute(path);

    app.innerHTML = `
        ${headerHtml(path)}
        <main class="page" id="view"></main>
        <footer class="site-footer">校淘 CampusMart · 校园二手交易与秒杀平台</footer>
    `;

    const logoutBtn = document.getElementById('logout-btn');
    logoutBtn?.addEventListener('click', () => {
        session.clear();
        toast('已退出登录');
        window.location.hash = '#/';
        render();
    });

    const viewRoot = document.getElementById('view');

    if (!matched) {
        viewRoot.innerHTML = `
            <div style="max-width:520px;margin:60px auto;text-align:center;display:grid;gap:12px">
                <h1 class="section-title">页面不存在</h1>
                <p class="section-sub">地址可能已经变更，回首页看看在售的闲置。</p>
                <a class="btn btn-primary" href="#/" style="justify-self:center">回到首页</a>
            </div>`;
        return;
    }

    const params = { ...matched.params };
    query.forEach((value, key) => {
        params[key] = value;
    });

    try {
        cleanupCurrentView = await matched.route.view(viewRoot, params);
    } catch (error) {
        viewRoot.innerHTML = `<div class="alert alert--error" role="alert">${escapeText(error.message)}</div>`;
    }

    window.scrollTo({ top: 0 });
}

window.addEventListener('hashchange', render);
render();
