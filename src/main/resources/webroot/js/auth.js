// Admin auth gate: wraps fetch to attach X-API-Key on /api/* calls, shows a
// login overlay when no key is stored or a 401 comes back. Must load before app.js.
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
            'background:rgba(15,16,22,0.92)', 'backdrop-filter:blur(6px)',
            'display:flex', 'align-items:center', 'justify-content:center',
            'font-family:Manrope,system-ui,sans-serif', 'color:#f4f5f8'
        ].join(';') + ';';

        overlay.innerHTML = `
            <form id="ws-auth-form" autocomplete="off" style="background:#1c1f29;border:1px solid #2c303d;border-radius:14px;padding:32px;width:min(420px,92vw);box-shadow:0 24px 60px rgba(0,0,0,0.55);">
                <h2 style="margin:0 0 6px;font-size:22px;font-weight:700;letter-spacing:-0.01em;">Webhook Service</h2>
                <p style="margin:0 0 18px;font-size:13px;color:#9aa0b3;">Введите admin API-ключ, чтобы открыть панель.</p>
                <label for="ws-auth-input" style="display:block;margin-bottom:6px;font-size:12px;color:#9aa0b3;text-transform:uppercase;letter-spacing:0.06em;">API key</label>
                <input id="ws-auth-input" type="password" autocomplete="current-password" required
                       style="width:100%;padding:10px 12px;border-radius:8px;border:1px solid #2c303d;background:#11141c;color:#f4f5f8;font-family:'IBM Plex Mono',monospace;font-size:14px;outline:none;" />
                <div id="ws-auth-error" style="display:none;margin-top:10px;color:#ff7a7a;font-size:13px;"></div>
                <button id="ws-auth-submit" type="submit"
                        style="margin-top:18px;width:100%;padding:11px 14px;border-radius:8px;border:none;background:#6c8cff;color:#0b0d13;font-weight:600;font-size:14px;cursor:pointer;">
                    Войти
                </button>
                <p style="margin:16px 0 0;font-size:12px;color:#717789;line-height:1.5;">
                    Ключ задаётся в конфигурации сервера через переменную <code style="background:#11141c;padding:1px 6px;border-radius:4px;">ADMIN_API_KEY</code>.
                </p>
            </form>
        `;
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
        // Bypass the wrapped fetch to avoid the global 401 interceptor here.
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
            // Server unreachable — don't block; app.js will surface the error.
            console.warn('Auth verify failed at bootstrap:', err);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrap);
    } else {
        bootstrap();
    }
})();
