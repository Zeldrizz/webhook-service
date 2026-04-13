/**
 * Webhook detail page.
 * Displays configuration, stats, and request logs with pagination.
 */

import API from '../api.js';
import { showNotification } from '../components/notification.js';

export async function renderWebhookDetail(container, webhookId) {
    const pageSize = 20;
    let webhook;
    let stats;
    let currentPage = 0;

    container.innerHTML = `
        <div class="text-center py-5"><div class="spinner-border"></div></div>
    `;

    try {
        [webhook, stats] = await Promise.all([
            API.getWebhook(webhookId),
            API.getStats(webhookId)
        ]);
    } catch (error) {
        container.innerHTML = `
            <div class="alert alert-danger">${escapeHtml(error.message)}</div>
        `;
        return;
    }

    container.innerHTML = `
        <div class="d-flex flex-column flex-lg-row justify-content-between align-items-lg-center gap-3 mb-4">
            <div>
                <h2 class="mb-1">${escapeHtml(webhook.name)}</h2>
                <div class="text-muted">Slug: <code>${escapeHtml(webhook.slug)}</code></div>
            </div>
            <div class="d-flex gap-2 flex-wrap">
                <button id="btn-copy-url" class="btn btn-outline-secondary">Copy URL</button>
                <a href="#create?id=${webhook.id}" class="btn btn-outline-primary">Edit</a>
                <button id="btn-toggle" class="btn ${webhook.isActive ? 'btn-outline-warning' : 'btn-outline-success'}">${webhook.isActive ? 'Disable' : 'Enable'}</button>
                <a href="#dashboard" class="btn btn-outline-dark">Back</a>
            </div>
        </div>

        <div class="row g-3 mb-4">
            <div class="col-sm-6 col-xl-3">
                <div class="card h-100"><div class="card-body">
                    <div class="text-muted small">Total requests</div>
                    <div class="fs-3 fw-semibold">${stats.totalRequests ?? 0}</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card h-100"><div class="card-body">
                    <div class="text-muted small">Today</div>
                    <div class="fs-3 fw-semibold">${stats.todayRequests ?? 0}</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card h-100"><div class="card-body">
                    <div class="text-muted small">Last request</div>
                    <div class="fw-semibold">${stats.lastRequestAt ? formatDate(stats.lastRequestAt) : '-'}</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card h-100"><div class="card-body">
                    <div class="text-muted small">Methods breakdown</div>
                    <div class="fw-semibold">${renderMethodCounts(stats.methodCounts)}</div>
                </div></div>
            </div>
        </div>

        <div class="row g-4 mb-4">
            <div class="col-lg-6">
                <div class="card h-100">
                    <div class="card-header">Configuration</div>
                    <div class="card-body">
                        <dl class="row mb-0">
                            <dt class="col-sm-4">Endpoint</dt>
                            <dd class="col-sm-8"><code class="app-code-inline">${escapeHtml(webhook.endpointUrl)}</code></dd>
                            <dt class="col-sm-4">Methods</dt>
                            <dd class="col-sm-8"><code>${escapeHtml(webhook.methods)}</code></dd>
                            <dt class="col-sm-4">Status</dt>
                            <dd class="col-sm-8">${webhook.isActive ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Inactive</span>'}</dd>
                            <dt class="col-sm-4">Debug</dt>
                            <dd class="col-sm-8">${webhook.debugMode ? 'Enabled' : 'Disabled'}</dd>
                            <dt class="col-sm-4">Max logs</dt>
                            <dd class="col-sm-8">${webhook.maxLogCount}</dd>
                            <dt class="col-sm-4">Created</dt>
                            <dd class="col-sm-8">${formatDate(webhook.createdAt)}</dd>
                            <dt class="col-sm-4">Updated</dt>
                            <dd class="col-sm-8">${formatDate(webhook.updatedAt)}</dd>
                            <dt class="col-sm-4">Description</dt>
                            <dd class="col-sm-8">${escapeHtml(webhook.description || '-')}</dd>
                        </dl>
                    </div>
                </div>
            </div>
            <div class="col-lg-6">
                <div class="card h-100">
                    <div class="card-header">Proxy</div>
                    <div class="card-body">
                        <p class="mb-2"><strong>URL:</strong> ${webhook.proxyUrl ? `<code class="app-code-inline">${escapeHtml(webhook.proxyUrl)}</code>` : '—'}</p>
                        <p class="mb-2"><strong>Headers:</strong></p>
                        <pre class="app-preview-box mb-0">${escapeHtml(JSON.stringify(webhook.proxyHeaders || {}, null, 2))}</pre>
                    </div>
                </div>
            </div>
        </div>

        <div class="row g-4 mb-4">
            <div class="col-lg-6">
                <div class="card h-100">
                    <div class="card-header">Request template</div>
                    <div class="card-body">
                        <pre class="app-preview-box mb-0">${escapeHtml(webhook.requestTemplate || '—')}</pre>
                    </div>
                </div>
            </div>
            <div class="col-lg-6">
                <div class="card h-100">
                    <div class="card-header">Response template</div>
                    <div class="card-body">
                        <pre class="app-preview-box mb-0">${escapeHtml(webhook.responseTemplate || '—')}</pre>
                    </div>
                </div>
            </div>
        </div>

        <div class="card">
            <div class="card-header d-flex justify-content-between align-items-center gap-2 flex-wrap">
                <span>Request logs</span>
                <button id="btn-clear-logs" class="btn btn-outline-danger btn-sm">Clear logs</button>
            </div>
            <div class="card-body">
                <div id="logs-container"></div>
                <nav class="mt-3">
                    <ul id="logs-pagination" class="pagination pagination-sm mb-0"></ul>
                </nav>
            </div>
        </div>
    `;

    document.getElementById('btn-copy-url').addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(webhook.endpointUrl);
            showNotification('URL copied', 'success');
        } catch (error) {
            showNotification('Не удалось скопировать URL', 'error');
        }
    });

    document.getElementById('btn-toggle').addEventListener('click', async () => {
        try {
            await API.toggleWebhook(webhookId);
            showNotification('Статус обновлён', 'success');
            await renderWebhookDetail(container, webhookId);
        } catch (error) {
            showNotification(error.message, 'error');
        }
    });

    document.getElementById('btn-clear-logs').addEventListener('click', async () => {
        if (!window.confirm('Очистить все логи этого вебхука?')) {
            return;
        }
        try {
            await API.clearRequests(webhookId);
            showNotification('Логи очищены', 'success');
            await renderWebhookDetail(container, webhookId);
        } catch (error) {
            showNotification(error.message, 'error');
        }
    });

    await loadLogs(0);

    async function loadLogs(page) {
        currentPage = page;
        const logsContainer = document.getElementById('logs-container');
        logsContainer.innerHTML = `<div class="text-center py-4"><div class="spinner-border spinner-border-sm"></div></div>`;

        try {
            const data = await API.fetchRequests(webhookId, page, pageSize);
            renderLogs(data);
            renderPagination(data);
        } catch (error) {
            logsContainer.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(error.message)}</div>`;
            document.getElementById('logs-pagination').innerHTML = '';
        }
    }

    function renderLogs(data) {
        const logsContainer = document.getElementById('logs-container');
        if (!data.items.length) {
            logsContainer.innerHTML = `
                <div class="alert alert-light border mb-0">Пока нет ни одного запроса. Отправь запрос на endpoint выше.</div>
            `;
            return;
        }

        const rows = data.items.map(log => `
            <tr>
                <td>${formatDate(log.receivedAt)}</td>
                <td><span class="badge text-bg-light border">${escapeHtml(log.method)}</span></td>
                <td>${escapeHtml(log.sourceIp || '-')}</td>
                <td>${log.responseStatus === null ? '<span class="text-muted">-</span>' : `<span class="badge text-bg-info">${log.responseStatus}</span>`}</td>
                <td>${log.proxyDurationMs === null ? '<span class="text-muted">-</span>' : `${log.proxyDurationMs} ms`}</td>
                <td class="text-end"><a href="#request/${webhookId}/${log.id}" class="btn btn-outline-primary btn-sm">Details</a></td>
            </tr>
        `).join('');

        logsContainer.innerHTML = `
            <div class="table-responsive">
                <table class="table table-striped align-middle mb-0">
                    <thead>
                    <tr>
                        <th>Time</th>
                        <th>Method</th>
                        <th>Source IP</th>
                        <th>Proxy status</th>
                        <th>Duration</th>
                        <th></th>
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
        html += `
            <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${Math.max(0, currentPage - 1)}">&laquo;</a>
            </li>
        `;

        for (let page = 0; page < totalPages; page++) {
            html += `
                <li class="page-item ${page === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" data-page="${page}">${page + 1}</a>
                </li>
            `;
        }

        html += `
            <li class="page-item ${currentPage >= totalPages - 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${Math.min(totalPages - 1, currentPage + 1)}">&raquo;</a>
            </li>
        `;

        nav.innerHTML = html;
        nav.querySelectorAll('[data-page]').forEach(link => {
            link.addEventListener('click', async event => {
                event.preventDefault();
                const page = Number(link.dataset.page);
                if (Number.isFinite(page) && page !== currentPage) {
                    await loadLogs(page);
                }
            });
        });
    }
}

function renderMethodCounts(methodCounts) {
    const entries = Object.entries(methodCounts || {});
    if (!entries.length) {
        return '—';
    }
    return entries.map(([method, count]) => `${escapeHtml(method)}: ${count}`).join('<br>');
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}
