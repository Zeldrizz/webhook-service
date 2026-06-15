import API from '../api.js';
import { flushCacheWithConfirmation } from '../components/cacheAdmin.js';
import { showNotification } from '../components/notification.js';
import {
    actionLabel,
    confirmAction,
    copyText,
    escapeAttr,
    escapeHtml,
    formatDate,
    icon,
    parseMethods,
    renderDebugBadge,
    renderEmptyState,
    renderMethodBadges,
    renderPageItem,
    renderSkeletonTable,
    renderStatusBadge,
    renderStatusCodeBadge,
    withButtonLoading
} from '../components/ui.js';

const PAGE_SIZE = 20;

export async function renderWebhookDetail(container, webhookId) {
    const state = {
        webhook: null,
        stats: null,
        currentPage: 0,
        methodFilter: 'all',
        statusFilter: 'all'
    };

    renderLoading(container);

    try {
        const [webhook, stats] = await Promise.all([
            API.getWebhook(webhookId),
            API.getStats(webhookId)
        ]);
        state.webhook = webhook;
        state.stats = stats;
    } catch (error) {
        renderLoadError(container, error);
        return;
    }

    renderPage();
    bindPageActions();
    await loadLogs(0);

    function renderPage() {
        const webhook = state.webhook;
        const stats = state.stats;
        container.innerHTML = `
            <section class="app-page-head">
                <div>
                    <div class="app-page-kicker">Webhook profile</div>
                    <h1 class="app-page-title">${escapeHtml(webhook.name)}</h1>
                    <p class="app-page-subtitle">
                        <code>${escapeHtml(webhook.slug)}</code>
                        <span class="app-inline-badges">${renderStatusBadge(webhook.isActive)} ${renderDebugBadge(webhook.debugMode)}</span>
                    </p>
                </div>
                <div class="app-actions">
                    <button id="btn-copy-url" class="btn btn-app-ghost" type="button">${actionLabel('clipboard', 'URL')}</button>
                    <button id="btn-test-request" class="btn btn-app-ghost" type="button">${actionLabel('send', 'Тест')}</button>
                    <a href="#edit/${escapeAttr(webhook.id)}" class="btn btn-app-ghost">${actionLabel('pencil', 'Изменить')}</a>
                    <button id="btn-toggle" class="btn ${webhook.isActive ? 'btn-app-tonal' : 'btn-app-primary'}" type="button">
                        ${actionLabel(webhook.isActive ? 'pause-fill' : 'play-fill', webhook.isActive ? 'Выключить' : 'Включить')}
                    </button>
                    <button id="btn-flush-cache" class="btn btn-app-ghost" type="button">${actionLabel('stars', 'Flush')}</button>
                    <button id="btn-delete" class="btn btn-app-danger" type="button">${actionLabel('trash3', 'Удалить')}</button>
                    <a href="#dashboard" class="btn btn-app-ghost">${actionLabel('arrow-left', 'Назад')}</a>
                </div>
            </section>

            <section class="app-stat-grid" aria-label="Статистика вебхука">
                ${statCard('Всего запросов', stats.totalRequests ?? 0, 'Все сохранённые запросы')}
                ${statCard('Сегодня', stats.todayRequests ?? 0, 'С момента локальной полуночи')}
                ${statCard('Последний', stats.lastRequestAt ? formatDate(stats.lastRequestAt) : '-', 'Последняя активность endpoint')}
                ${statCard('Методы', renderMethodCounts(stats.methodCounts), 'Распределение запросов')}
            </section>

            <section class="app-detail-grid">
                <div class="app-panel">
                    <div class="app-panel-head">
                        <h2>Конфигурация</h2>
                    </div>
                    <dl class="app-def-list">
                        <dt>Endpoint</dt>
                        <dd>
                            <div class="app-endpoint-copy">
                                <code class="app-code-inline">${escapeHtml(webhook.endpointUrl)}</code>
                                <button class="btn btn-icon btn-app-ghost" id="btn-copy-url-inline" type="button" title="Скопировать endpoint">${icon('clipboard')}</button>
                            </div>
                        </dd>
                        <dt>Методы</dt>
                        <dd><div class="app-badge-stack">${renderMethodBadges(webhook.methods)}</div></dd>
                        <dt>Max logs</dt>
                        <dd>${escapeHtml(webhook.maxLogCount)}</dd>
                        <dt>Создан</dt>
                        <dd>${formatDate(webhook.createdAt)}</dd>
                        <dt>Обновлён</dt>
                        <dd>${formatDate(webhook.updatedAt)}</dd>
                        <dt>Описание</dt>
                        <dd>${escapeHtml(webhook.description || '-')}</dd>
                    </dl>
                </div>

                <div class="app-panel">
                    <div class="app-panel-head">
                        <h2>Проксирование</h2>
                    </div>
                    <dl class="app-def-list">
                        <dt>Proxy URL</dt>
                        <dd>${webhook.proxyUrl ? `<code class="app-code-inline">${escapeHtml(webhook.proxyUrl)}</code>` : '-'}</dd>
                        <dt>Headers</dt>
                        <dd><pre class="app-preview-box compact mb-0">${escapeHtml(JSON.stringify(webhook.proxyHeaders || {}, null, 2))}</pre></dd>
                    </dl>
                </div>
            </section>

            <section class="app-detail-grid">
                <div class="app-panel">
                    <div class="app-panel-head"><h2>Request template</h2></div>
                    <pre class="app-preview-box compact mb-0">${escapeHtml(webhook.requestTemplate || 'Не задан. В proxy уйдёт исходное тело запроса.')}</pre>
                </div>
                <div class="app-panel">
                    <div class="app-panel-head"><h2>Response template</h2></div>
                    <pre class="app-preview-box compact mb-0">${escapeHtml(webhook.responseTemplate || 'Не задан. Клиент получит proxy response или accepted response.')}</pre>
                </div>
            </section>

            <section class="app-panel">
                <div class="app-panel-head app-panel-head-with-actions">
                    <div>
                        <h2>История запросов</h2>
                        <p>Фильтры применяются на API и сохраняют пагинацию без перезагрузки страницы.</p>
                    </div>
                    <button id="btn-clear-logs" class="btn btn-app-danger btn-sm" type="button">${actionLabel('eraser', 'Очистить')}</button>
                </div>

                <div class="app-toolbar compact" aria-label="Фильтры запросов">
                    <div>
                        <label class="form-label" for="logs-method-filter">Метод</label>
                        <select id="logs-method-filter" class="form-select">
                            <option value="all">Все</option>
                            ${parseMethods(webhook.methods).map(method => `<option value="${escapeAttr(method)}">${escapeHtml(method)}</option>`).join('')}
                        </select>
                    </div>
                    <div>
                        <label class="form-label" for="logs-status-filter">Статус</label>
                        <select id="logs-status-filter" class="form-select">
                            <option value="all">Все</option>
                            <option value="2xx">2xx success</option>
                            <option value="3xx">3xx redirect</option>
                            <option value="4xx">4xx client</option>
                            <option value="5xx">5xx server</option>
                            <option value="none">Без proxy ответа</option>
                        </select>
                    </div>
                    <div class="app-toolbar-spacer"></div>
                    <button id="btn-refresh-logs" class="btn btn-app-ghost" type="button">${actionLabel('arrow-clockwise', 'Обновить')}</button>
                </div>

                <div id="logs-feedback" aria-live="polite"></div>
                <div id="logs-container"></div>
                <nav class="mt-3" aria-label="Страницы запросов">
                    <ul id="logs-pagination" class="pagination pagination-sm mb-0"></ul>
                </nav>
            </section>
        `;
    }

    function bindPageActions() {
        document.getElementById('logs-method-filter').value = state.methodFilter;
        document.getElementById('logs-status-filter').value = state.statusFilter;
        document.getElementById('btn-copy-url').addEventListener('click', copyEndpoint);
        document.getElementById('btn-copy-url-inline').addEventListener('click', copyEndpoint);
        document.getElementById('btn-test-request').addEventListener('click', sendTestRequest);
        document.getElementById('btn-delete').addEventListener('click', deleteWebhook);
        document.getElementById('btn-toggle').addEventListener('click', toggleWebhook);
        document.getElementById('btn-clear-logs').addEventListener('click', clearLogs);
        document.getElementById('btn-flush-cache').addEventListener('click', event => flushCacheWithConfirmation(event.currentTarget));
        document.getElementById('btn-refresh-logs').addEventListener('click', () => loadLogs(state.currentPage));
        document.getElementById('logs-method-filter').addEventListener('change', event => {
            state.methodFilter = event.target.value;
            loadLogs(0);
        });
        document.getElementById('logs-status-filter').addEventListener('change', event => {
            state.statusFilter = event.target.value;
            loadLogs(0);
        });
    }

    async function copyEndpoint() {
        try {
            await copyText(state.webhook.endpointUrl, 'Endpoint URL скопирован', showNotification);
        } catch {
            showNotification('Не удалось скопировать URL', 'error');
        }
    }

    async function sendTestRequest(event) {
        const btn = event.currentTarget;
        await withButtonLoading(btn, 'Отправляю...', async () => {
            const testMethod = pickTestMethod(state.webhook.methods);
            const requestUrl = buildTestRequestUrl(state.webhook.endpointUrl, testMethod);
            const requestOptions = buildTestRequestOptions(testMethod);

            try {
                const response = await fetch(requestUrl, requestOptions);
                const data = await parseResponsePayload(response);

                if (!response.ok) {
                    const errorMessage = typeof data?.message === 'string'
                        ? data.message
                        : `HTTP ${response.status}`;
                    throw new Error(`${testMethod} failed: ${errorMessage}`);
                }

                const requestId = typeof data?.requestId === 'string' ? data.requestId : 'unknown';
                showNotification(`Тестовый ${testMethod} отправлен. Request ID: ${requestId}`, 'success');
                state.stats = await API.getStats(webhookId);
                renderPage();
                bindPageActions();
                await loadLogs(0);
            } catch (error) {
                showNotification('Тестовый запрос не прошёл: ' + error.message, 'error');
            }
        });
    }

    async function deleteWebhook() {
        const confirmed = await confirmAction({
            title: 'Удалить вебхук',
            message: `Удалить "${state.webhook.name}" и всю историю запросов?`,
            confirmText: 'Удалить',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await API.deleteWebhook(state.webhook.id);
            showNotification('Вебхук удалён', 'success');
            window.location.hash = '#dashboard';
        } catch (error) {
            showNotification('Удаление не удалось: ' + error.message, 'error');
        }
    }

    async function toggleWebhook(event) {
        await withButtonLoading(event.currentTarget, 'Обновляю...', async () => {
            try {
                state.webhook = await API.toggleWebhook(webhookId);
                showNotification('Статус обновлён', 'success');
                renderPage();
                bindPageActions();
                await loadLogs(state.currentPage);
            } catch (error) {
                showNotification(error.message, 'error');
            }
        });
    }

    async function clearLogs() {
        const confirmed = await confirmAction({
            title: 'Очистить историю',
            message: 'Удалить все сохранённые запросы этого вебхука? Конфигурация вебхука останется.',
            confirmText: 'Очистить',
            danger: true
        });
        if (!confirmed) {
            return;
        }
        try {
            await API.clearRequests(webhookId);
            showNotification('История очищена', 'success');
            state.stats = await API.getStats(webhookId);
            renderPage();
            bindPageActions();
            await loadLogs(0);
        } catch (error) {
            showNotification(error.message, 'error');
        }
    }

    async function loadLogs(page) {
        state.currentPage = page;
        const logsContainer = document.getElementById('logs-container');
        const feedback = document.getElementById('logs-feedback');
        logsContainer.innerHTML = renderSkeletonTable(6, 4);
        feedback.innerHTML = '';

        try {
            const data = await API.fetchRequests(webhookId, page, PAGE_SIZE, {
                method: state.methodFilter,
                status: state.statusFilter
            });
            renderLogs(data);
            renderPagination(data);
        } catch (error) {
            logsContainer.innerHTML = '';
            feedback.innerHTML = `<div class="alert alert-danger mb-3">${escapeHtml(error.message)}</div>`;
            document.getElementById('logs-pagination').innerHTML = '';
        }
    }

    function renderLogs(data) {
        const logsContainer = document.getElementById('logs-container');
        if (!data.items.length) {
            logsContainer.innerHTML = renderEmptyState({
                title: 'Запросов пока нет',
                message: 'Отправьте тестовый запрос или дождитесь реального события.'
            });
            return;
        }

        const rows = data.items.map(log => `
            <tr>
                <td>
                    <div class="app-row-title">${formatDate(log.receivedAt)}</div>
                    <div class="app-row-subtitle">${escapeHtml(log.id)}</div>
                </td>
                <td><span class="app-badge is-method">${escapeHtml(log.method)}</span></td>
                <td>${renderStatusCodeBadge(log.responseStatus)}</td>
                <td>${log.proxyDurationMs === null ? '<span class="text-muted">-</span>' : `${escapeHtml(log.proxyDurationMs)} ms`}</td>
                <td>${escapeHtml(log.sourceIp || '-')}</td>
                <td class="text-end">
                    <a href="#request/${escapeAttr(webhookId)}/${escapeAttr(log.id)}" class="btn btn-sm btn-app-ghost">${actionLabel('braces', 'Детали')}</a>
                </td>
            </tr>
        `).join('');

        logsContainer.innerHTML = `
            <div class="table-responsive">
                <table class="table table-hover align-middle mb-0">
                    <thead>
                        <tr>
                            <th>Время</th>
                            <th>Метод</th>
                            <th>Proxy status</th>
                            <th>Duration</th>
                            <th>Source IP</th>
                            <th class="text-end">Просмотр</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        `;
    }

    function renderPagination(data) {
        const nav = document.getElementById('logs-pagination');
        const totalPages = Math.ceil((data.total || 0) / data.size);

        if (totalPages <= 1) {
            nav.innerHTML = '';
            return;
        }

        let html = '';
        html += renderPageItem('Назад', Math.max(0, state.currentPage - 1), state.currentPage === 0);
        for (let page = 0; page < totalPages; page++) {
            html += renderPageItem(String(page + 1), page, false, page === state.currentPage);
        }
        html += renderPageItem('Вперёд', Math.min(totalPages - 1, state.currentPage + 1), state.currentPage >= totalPages - 1);

        nav.innerHTML = html;
        nav.querySelectorAll('[data-page]').forEach(link => {
            link.addEventListener('click', async event => {
                event.preventDefault();
                const page = Number(link.dataset.page);
                if (Number.isFinite(page) && page !== state.currentPage) {
                    await loadLogs(page);
                }
            });
        });
    }
}

function renderLoading(container) {
    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Webhook profile</div>
                <h1 class="app-page-title">Загружаю вебхук</h1>
                <p class="app-page-subtitle">Подтягиваю конфигурацию, статистику и историю.</p>
            </div>
        </section>
        ${renderSkeletonTable(5, 5)}
    `;
}

function renderLoadError(container, error) {
    container.innerHTML = `
        <div class="alert alert-danger app-alert-row">
            <div>
                <div class="fw-semibold">Не удалось загрузить вебхук</div>
                <div>${escapeHtml(error.message)}</div>
            </div>
            <a class="btn btn-app-ghost" href="#dashboard">${actionLabel('arrow-left', 'Назад')}</a>
        </div>
    `;
}

function statCard(label, value, meta) {
    return `
        <div class="app-stat-card">
            <div class="app-stat-label">${escapeHtml(label)}</div>
            <div class="app-stat-value">${typeof value === 'string' && value.includes('<') ? value : escapeHtml(value)}</div>
            <div class="app-stat-meta">${escapeHtml(meta)}</div>
        </div>
    `;
}

function renderMethodCounts(methodCounts) {
    const entries = Object.entries(methodCounts || {});
    if (!entries.length) {
        return '-';
    }
    return entries.map(([method, count]) => `<span class="app-badge is-method">${escapeHtml(method)} ${escapeHtml(count)}</span>`).join(' ');
}

function pickTestMethod(methods) {
    const allowedMethods = parseMethods(methods);
    if (allowedMethods.includes('POST')) {
        return 'POST';
    }
    return allowedMethods[0] || 'POST';
}

function buildTestRequestUrl(endpointUrl, method) {
    if (method !== 'GET') {
        return endpointUrl;
    }

    const url = new URL(endpointUrl, window.location.origin);
    url.searchParams.set('test', 'true');
    url.searchParams.set('timestamp', String(Date.now()));
    return url.toString();
}

function buildTestRequestOptions(method) {
    const options = { method };
    if (method === 'GET') {
        return options;
    }

    options.headers = { 'Content-Type': 'application/json' };
    options.body = JSON.stringify({
        event: 'demo.test',
        test: true,
        timestamp: Date.now()
    });
    return options;
}

async function parseResponsePayload(response) {
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }

    const text = await response.text();
    try {
        return JSON.parse(text);
    } catch {
        return { raw: text };
    }
}
