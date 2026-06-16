import API from '../api.js';
import { renderJsonViewer } from '../components/jsonViewer.js';
import { showNotification } from '../components/notification.js';
import {
    actionLabel,
    copyText,
    escapeAttr,
    escapeHtml,
    formatDate,
    icon,
    renderEmptyState,
    renderStatusCodeBadge,
    renderSkeletonTable
} from '../components/ui.js';

export async function renderRequestDetail(container, webhookId, requestId) {
    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Request trace</div>
                <h1 class="app-page-title">Загружаю запрос</h1>
                <p class="app-page-subtitle">Готовлю заголовки, параметры, тело и ответ прокси.</p>
            </div>
        </section>
        ${renderSkeletonTable(4, 4)}
    `;

    try {
        const requestLog = await API.getRequest(webhookId, requestId);

        container.innerHTML = `
            <section class="app-page-head">
                <div>
                    <div class="app-page-kicker">Трассировка запроса</div>
                    <h1 class="app-page-title">${escapeHtml(requestLog.method)} запрос</h1>
                    <p class="app-page-subtitle">
                        <code>${escapeHtml(requestLog.id)}</code>
                        <span class="app-inline-badges">${renderStatusCodeBadge(requestLog.responseStatus)}</span>
                    </p>
                </div>
                <div class="app-actions">
                    <button id="btn-copy-request-id" class="btn btn-app-ghost" type="button">${actionLabel('clipboard', 'ID')}</button>
                    <a href="#webhook/${escapeAttr(webhookId)}" class="btn btn-app-ghost">${actionLabel('arrow-left', 'К вебхуку')}</a>
                </div>
            </section>

            <section class="app-panel">
                <div class="app-panel-head">
                    <h2>Сводка</h2>
                </div>
                <div class="app-summary-grid">
                    ${summaryItem('Метод', `<span class="app-badge is-method">${escapeHtml(requestLog.method)}</span>`)}
                    ${summaryItem('URL', `<code class="app-code-inline">${escapeHtml(requestLog.url)}</code>`)}
                    ${summaryItem('Получен', escapeHtml(formatDate(requestLog.receivedAt)))}
                    ${summaryItem('IP источника', escapeHtml(requestLog.sourceIp || '-'))}
                    ${summaryItem('Content-Type', escapeHtml(requestLog.contentType || '-'))}
                    ${summaryItem('Время прокси', requestLog.proxyDurationMs === null ? '-' : `${escapeHtml(requestLog.proxyDurationMs)} ms`)}
                </div>
            </section>

            <section class="app-detail-grid">
                ${sectionShell('Заголовки', 'section-headers', 'headers')}
                ${sectionShell('Параметры запроса', 'section-query', 'query')}
            </section>

            <section class="app-panel">
                ${sectionHead('Тело запроса', 'body')}
                <div id="section-body"></div>
            </section>

            <section class="app-panel">
                ${sectionHead('Ответ прокси', 'proxy')}
                <div id="section-proxy"></div>
            </section>
        `;

        renderContent('section-headers', requestLog.headers, 'headers');
        renderContent('section-query', requestLog.queryParams, 'query');
        renderContent('section-body', requestLog.body, 'body');
        renderContent('section-proxy', requestLog.proxyResponse, 'proxy');

        document.getElementById('btn-copy-request-id').addEventListener('click', async () => {
            try {
                await copyText(requestLog.id, 'Request ID скопирован', showNotification);
            } catch {
                showNotification('Не удалось скопировать ID', 'error');
            }
        });

        document.querySelectorAll('[data-copy-section]').forEach(button => {
            button.addEventListener('click', async () => {
                const key = button.dataset.copySection;
                try {
                    await copyText(sectionRawValue(requestLog, key), 'Секция скопирована', showNotification);
                } catch {
                    showNotification('Не удалось скопировать секцию', 'error');
                }
            });
        });
    } catch (error) {
        container.innerHTML = `
            <div class="alert alert-danger app-alert-row">
                <div>
                    <div class="fw-semibold">Не удалось загрузить запрос</div>
                    <div>${escapeHtml(error.message)}</div>
                </div>
                <a href="#webhook/${escapeAttr(webhookId)}" class="btn btn-app-ghost">${actionLabel('arrow-left', 'К вебхуку')}</a>
            </div>
        `;
    }
}

function sectionShell(title, elementId, copyKey) {
    return `
        <div class="app-panel">
            ${sectionHead(title, copyKey)}
            <div id="${escapeAttr(elementId)}"></div>
        </div>
    `;
}

function sectionHead(title, copyKey) {
    return `
        <div class="app-panel-head app-panel-head-with-actions">
            <h2>${escapeHtml(title)}</h2>
            <button class="btn btn-icon btn-app-ghost" type="button" title="Скопировать" data-copy-section="${escapeAttr(copyKey)}">
                ${icon('clipboard')}
            </button>
        </div>
    `;
}

function summaryItem(label, valueHtml) {
    return `
        <div class="app-summary-item">
            <div class="app-summary-label">${escapeHtml(label)}</div>
            <div class="app-summary-value">${valueHtml}</div>
        </div>
    `;
}

function renderContent(elementId, rawValue) {
    const el = document.getElementById(elementId);
    if (!el) {
        return;
    }

    if (rawValue == null || rawValue === '') {
        el.innerHTML = renderEmptyState({ title: 'Пусто', message: 'В этой секции нет данных.' });
        return;
    }

    if (typeof rawValue === 'object') {
        if (!Object.keys(rawValue).length) {
            el.innerHTML = renderEmptyState({ title: 'Пусто', message: 'В этой секции нет данных.' });
            return;
        }
        renderJsonViewer(el, rawValue);
        return;
    }

    try {
        renderJsonViewer(el, JSON.parse(rawValue));
    } catch {
        el.innerHTML = `<pre class="app-preview-box mb-0">${escapeHtml(rawValue)}</pre>`;
    }
}

function sectionRawValue(requestLog, key) {
    const value = {
        headers: requestLog.headers,
        query: requestLog.queryParams,
        body: requestLog.body,
        proxy: requestLog.proxyResponse
    }[key];

    if (value == null) {
        return '';
    }
    return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}
