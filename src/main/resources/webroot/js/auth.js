(function () {
    const STORAGE_KEY = 'ws.adminApiKey';
    const HEADER_NAME = 'X-API-Key';
    const VERIFY_URL = '/api/auth/verify';

    function getKey() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) || '';
        } catch (e) {
            return '';
        }
    }

    function setKey(value) {
        try {
            window.localStorage.setItem(STORAGE_KEY, value);
        } catch (e) {
            // localStorage may be unavailable in private contexts.
        }
    }

    function clearKey() {
        try {
            window.localStorage.removeItem(STORAGE_KEY);
        } catch (e) {
            // localStorage may be unavailable in private contexts.
        }
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
                showLoginOverlay('Ключ не подошёл или сессия устарела. Проверьте ADMIN_API_KEY и войдите снова.');
            }
            return response;
        });
    };

    function buildOverlay() {
        const overlay = document.createElement('div');
        overlay.id = 'ws-auth-overlay';
        overlay.className = 'ws-auth-overlay';

        overlay.innerHTML = `
            <form id="ws-auth-form" class="ws-auth-card" autocomplete="off">
                <div class="ws-auth-mark"><i class="bi bi-shield-lock" aria-hidden="true"></i></div>
                <h2>Webhook Service</h2>
                <p>Введите admin API-ключ, чтобы открыть панель управления и защищённые `/api/*` маршруты.</p>
                <label for="ws-auth-input" class="form-label">API key</label>
                <input id="ws-auth-input" class="form-control app-code-input" type="password" autocomplete="current-password" required placeholder="ADMIN_API_KEY">
                <div id="ws-auth-error" class="ws-auth-error" role="alert"></div>
                <button id="ws-auth-submit" class="btn btn-app-primary w-100" type="submit">
                    <i class="bi bi-box-arrow-in-right" aria-hidden="true"></i><span>Войти</span>
                </button>
                <div class="ws-auth-hint">
                    По умолчанию в demo-окружении используется ключ из переменной <code>ADMIN_API_KEY</code>.
                </div>
            </form>
        `;

        return overlay;
    }

    let overlayEl = null;

    function showLoginOverlay(message) {
        if (overlayEl) {
            showOverlayError(message);
            return;
        }
        overlayEl = buildOverlay();
        document.body.appendChild(overlayEl);

        const form = overlayEl.querySelector('#ws-auth-form');
        const input = overlayEl.querySelector('#ws-auth-input');
        const error = overlayEl.querySelector('#ws-auth-error');
        const submit = overlayEl.querySelector('#ws-auth-submit');

        showOverlayError(message);
        window.setTimeout(() => input.focus(), 50);

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            error.textContent = '';
            const candidate = input.value.trim();
            if (!candidate) {
                error.textContent = 'Введите API-ключ.';
                return;
            }
            submit.disabled = true;
            submit.innerHTML = '<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>Проверяю...</span>';
            try {
                const ok = await verifyKey(candidate);
                if (ok) {
                    setKey(candidate);
                    hideLoginOverlay();
                    window.location.reload();
                } else {
                    error.textContent = 'Неверный API-ключ. Проверьте значение ADMIN_API_KEY.';
                }
            } catch (err) {
                error.textContent = 'Не удалось связаться с сервером: ' + (err.message || err);
            } finally {
                submit.disabled = false;
                submit.innerHTML = '<i class="bi bi-box-arrow-in-right" aria-hidden="true"></i><span>Войти</span>';
            }
        });
    }

    function showOverlayError(message) {
        if (!overlayEl) {
            return;
        }
        const error = overlayEl.querySelector('#ws-auth-error');
        error.textContent = message || '';
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
