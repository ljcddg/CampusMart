import { api, session } from '../api.js';
import { toast } from '../ui.js';

export async function renderAuth(root, params = {}) {
    const initialTab = params.mode === 'register' ? 'register' : 'login';

    root.innerHTML = `
        <div class="auth-wrap">
            <div class="auth-card">
                <h1 class="auth-title">${initialTab === 'register' ? '创建校淘账号' : '登录校淘'}</h1>
                <p class="auth-sub">同校面交，闲置直接对接下一位同学</p>

                <div class="tabs" role="tablist">
                    <button class="tab" role="tab" data-tab="login"
                            aria-selected="${initialTab === 'login'}">登录</button>
                    <button class="tab" role="tab" data-tab="register"
                            aria-selected="${initialTab === 'register'}">注册</button>
                </div>

                <form id="auth-form" novalidate>
                    <div class="field">
                        <label for="username">用户名</label>
                        <input class="input" id="username" name="username" autocomplete="username"
                               placeholder="4 到 32 位字母或数字">
                    </div>

                    <div class="field" id="nickname-field" ${initialTab === 'register' ? '' : 'hidden'}>
                        <label for="nickname">昵称（选填）</label>
                        <input class="input" id="nickname" name="nickname" autocomplete="nickname"
                               placeholder="同学怎么称呼你">
                    </div>

                    <div class="field">
                        <label for="password">密码</label>
                        <input class="input" id="password" name="password" type="password"
                               autocomplete="current-password" placeholder="至少 6 位">
                    </div>

                    <div id="auth-error" class="field-error" role="alert" hidden></div>

                    <button class="btn btn-primary btn-block" type="submit" id="auth-submit">登录</button>
                </form>

                <p class="demo-hint">
                    演示账号：<strong>test01</strong> / <strong>123456</strong>，也可直接注册新账号。
                </p>
            </div>
        </div>
    `;

    const form = root.querySelector('#auth-form');
    const tabs = root.querySelectorAll('[data-tab]');
    const nicknameField = root.querySelector('#nickname-field');
    const submitBtn = root.querySelector('#auth-submit');
    const errorEl = root.querySelector('#auth-error');
    let mode = initialTab;

    function setMode(next) {
        mode = next;
        tabs.forEach((tab) => {
            tab.setAttribute('aria-selected', String(tab.dataset.tab === next));
        });
        nicknameField.hidden = next !== 'register';
        submitBtn.textContent = next === 'register' ? '注册并登录' : '登录';
        errorEl.hidden = true;
    }

    tabs.forEach((tab) => {
        tab.addEventListener('click', () => setMode(tab.dataset.tab));
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        errorEl.hidden = true;

        const username = root.querySelector('#username').value.trim();
        const password = root.querySelector('#password').value;
        const nickname = root.querySelector('#nickname').value.trim();

        if (!username || !password) {
            showError('用户名和密码都要填写');
            return;
        }
        if (mode === 'register' && password.length < 6) {
            showError('密码至少 6 位');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.textContent = mode === 'register' ? '注册中' : '登录中';

        try {
            if (mode === 'register') {
                await api.register({ username, password, nickname: nickname || null });
                toast('注册成功，正在登录', 'ok');
            }
            const result = await api.login(username, password);
            session.save(result.token, {
                userId: result.userId,
                username: result.username,
                nickname: result.nickname
            });
            toast(`欢迎，${result.nickname || result.username}`, 'ok');
            window.location.hash = params.redirect || '#/';
        } catch (error) {
            showError(error.message);
            submitBtn.disabled = false;
            submitBtn.textContent = mode === 'register' ? '注册并登录' : '登录';
        }
    });

    function showError(message) {
        errorEl.textContent = message;
        errorEl.hidden = false;
    }
}
