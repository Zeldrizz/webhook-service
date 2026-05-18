(function () {
    const STORAGE_KEY = 'ws.adminApiKey';
    const HEADER_NAME = 'X-API-Key';
    const VERIFY_URL = '/api/auth/verify';

    function getKey() {
        try { return window.localStorage.getItem(STORAGE_KEY) || ''; } catch (e) { return ''; }
    }

    function setKey(value) {
        try { window.localStorage.setItem(STORAGE_KEY, value); } catch (e) { /* ignore */ }
    }

    function clearKey() {
        try { window.localStorage.removeItem(STORAGE_KEY); } catch (e) { /* ignore */ }
    }

    function isApiPath(url) {
        if (!url) return false;
        if (typeof url !== 'string') url = url.url || '';
        return url.startsWith('/api/') || url.indexOf(window.location.origin + '/api/') === 0;
    }

    const originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
        const url = typeof input === 'string' ? input : (input && input.url);
        if (isApiPath(url)) {
            init = init || {};
            const headers = new Headers(init.headers || (typeof input === 'object' ? input.headers : undefined) || {});
            const key = getKey();
            if (key && !headers.has(HEADER_NAME)) {
                headers.set(HEADER_NAME, key);
            }
            init.headers = headers;
        }
        return originalFetch(input, init).then((response) => {
            if (response.status === 401 && isApiPath(url)) {
                clearKey();
                showLoginOverlay('Сессия истекла или ключ изменён. Войдите снова.');
            }
            return response;
        });
    };

    function buildOverlay() {
        const overlay = document.createElement('div');
        overlay.id = 'ws-auth-overlay';
        overlay.style.cssText = [
            'position:fixed', 'inset:0', 'z-index:9999',
            'background:rgba(235,228,216,0.72)', 'backdrop-filter:blur(20px)',
            'display:flex', 'align-items:center', 'justify-content:center',
            'font-family:Manrope,system-ui,sans-serif', 'color:#1f2722',
            'padding:24px'
        ].join(';') + ';';

        overlay.innerHTML = `
            <form id="ws-auth-form" autocomplete="off" style="background:linear-gradient(145deg,rgba(255,252,247,0.97),rgba(244,236,224,0.94));border:1px solid rgba(199,186,168,0.9);border-radius:1.55rem;padding:32px;width:min(420px,92vw);box-shadow:0 24px 60px rgba(48,37,25,0.18);">
                <h2 style="margin:0 0 6px;font-size:22px;font-weight:800;letter-spacing:-0.02em;color:#1f2722;display:flex;align-items:center;">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#435649"
                         stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"
                         style="margin-right:8px;vertical-align:-2px;">
                      <rect x="3" y="11" width="18" height="11" rx="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                    Webhook Service
                </h2>
                <p style="margin:0 0 18px;font-size:13px;color:#6a7269;line-height:1.55;">Введите admin API-ключ, чтобы открыть панель управления.</p>
                <label for="ws-auth-input" style="display:block;margin-bottom:6px;font-size:12px;color:#6a7269;text-transform:uppercase;letter-spacing:0.06em;font-weight:700;">API key</label>
                <input id="ws-auth-input" type="password" autocomplete="current-password" required
                       style="width:100%;padding:11px 13px;border-radius:1rem;border:1px solid rgba(199,186,168,0.9);background:rgba(255,255,255,0.74);color:#1f2722;font-family:'IBM Plex Mono',monospace;font-size:14px;outline:none;transition:border-color .16s ease,box-shadow .16s ease,background .16s ease;" />
                <div id="ws-auth-error" style="display:none;margin-top:10px;color:#8b5360;font-size:13px;font-weight:600;"></div>
                <button id="ws-auth-submit" type="submit"
                        style="margin-top:18px;width:100%;padding:12px 14px;border-radius:999px;border:none;background:linear-gradient(135deg,#435649,#2c3a31);color:#f8f5ef;font-weight:700;font-size:14px;cursor:pointer;transition:transform .16s ease,box-shadow .16s ease,background .16s ease;">
                    Войти
                </button>
                <p style="margin:16px 0 0;font-size:12px;color:#6a7269;line-height:1.55;">
                    Ключ задаётся в конфигурации сервера через переменную <code style="background:rgba(67,86,73,0.09);color:#1f2722;padding:2px 7px;border-radius:999px;">ADMIN_API_KEY</code>.
                </p>
            </form>
        `;

        const input = overlay.querySelector('#ws-auth-input');
        input.addEventListener('focus', () => {
            input.style.borderColor = 'rgba(67,86,73,0.6)';
            input.style.background = '#fffdfa';
            input.style.boxShadow = '0 0 0 0.24rem rgba(67,86,73,0.12)';
        });
        input.addEventListener('blur', () => {
            input.style.borderColor = 'rgba(199,186,168,0.9)';
            input.style.background = 'rgba(255,255,255,0.74)';
            input.style.boxShadow = 'none';
        });

        const btn = overlay.querySelector('#ws-auth-submit');
        btn.addEventListener('mouseenter', () => {
            if (!btn.disabled) {
                btn.style.transform = 'translateY(-1px)';
                btn.style.boxShadow = '0 12px 24px rgba(48,37,25,0.15)';
                btn.style.background = 'linear-gradient(135deg,#37463d,#223028)';
            }
        });
        btn.addEventListener('mouseleave', () => {
            btn.style.transform = '';
            btn.style.boxShadow = '';
            btn.style.background = 'linear-gradient(135deg,#435649,#2c3a31)';
        });

        return overlay;
    }

    let overlayEl = null;

    function showLoginOverlay(message) {
        if (overlayEl) {
            const err = overlayEl.querySelector('#ws-auth-error');
            if (message && err) {
                err.textContent = message;
                err.style.display = 'block';
            }
            return;
        }
        overlayEl = buildOverlay();
        document.body.appendChild(overlayEl);

        const form = overlayEl.querySelector('#ws-auth-form');
        const input = overlayEl.querySelector('#ws-auth-input');
        const error = overlayEl.querySelector('#ws-auth-error');
        const submit = overlayEl.querySelector('#ws-auth-submit');

        if (message) {
            error.textContent = message;
            error.style.display = 'block';
        }

        setTimeout(() => input.focus(), 50);

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            error.style.display = 'none';
            const candidate = input.value.trim();
            if (!candidate) return;
            submit.disabled = true;
            submit.textContent = 'Проверяю…';
            try {
                const ok = await verifyKey(candidate);
                if (ok) {
                    setKey(candidate);
                    hideLoginOverlay();
                    window.location.reload();
                } else {
                    error.textContent = 'Неверный API-ключ.';
                    error.style.display = 'block';
                }
            } catch (err) {
                error.textContent = 'Не удалось связаться с сервером: ' + (err.message || err);
                error.style.display = 'block';
            } finally {
                submit.disabled = false;
                submit.textContent = 'Войти';
            }
        });
    }

    function hideLoginOverlay() {
        if (overlayEl && overlayEl.parentNode) {
            overlayEl.parentNode.removeChild(overlayEl);
        }
        overlayEl = null;
    }

    async function verifyKey(candidate) {
        const response = await originalFetch(VERIFY_URL, {
            headers: { [HEADER_NAME]: candidate }
        });
        return response.ok;
    }

    window.WSAuth = {
        logout() {
            clearKey();
            showLoginOverlay('Вы вышли из панели.');
        },
        getKey,
        showLoginOverlay
    };

    async function bootstrap() {
        const stored = getKey();
        if (!stored) {
            showLoginOverlay();
            return;
        }
        try {
            const ok = await verifyKey(stored);
            if (!ok) {
                clearKey();
                showLoginOverlay('Сохранённый ключ больше не действителен.');
            }
        } catch (err) {
            console.warn('Auth verify failed at bootstrap:', err);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrap);
    } else {
        bootstrap();
    }
})();
