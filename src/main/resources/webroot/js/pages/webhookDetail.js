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
        <div class="app-page-head">
            <div>
                <div class="app-page-kicker">Webhook Profile</div>
                <h1 class="app-page-title">${escapeHtml(webhook.name)}</h1>
                <p class="app-page-subtitle">Delivery endpoint <code>${escapeHtml(webhook.slug)}</code></p>
            </div>
            <div class="app-actions">
                <button id="btn-copy-url" class="btn btn-outline-secondary">Copy URL</button>
                <button id="btn-test-request" class="btn btn-outline-secondary">Send Test Request</button>
                <a href="#create?id=${webhook.id}" class="btn btn-outline-secondary">Edit</a>
                <button id="btn-toggle" class="btn ${webhook.isActive ? 'btn-app-tonal' : 'btn-primary'}">${webhook.isActive ? 'Disable' : 'Enable'}</button>
                <button id="btn-delete" class="btn btn-app-danger">Delete</button>
                <a href="#dashboard" class="btn btn-outline-secondary">Back</a>
            </div>
        </div>

        <div class="row g-3 mb-4">
            <div class="col-sm-6 col-xl-3">
                <div class="card app-stat-card h-100"><div class="card-body">
                    <div>
                        <div class="app-stat-label">Total requests</div>
                        <div class="app-stat-value">${stats.totalRequests ?? 0}</div>
                    </div>
                    <div class="app-stat-meta">All captured deliveries for this webhook.</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card app-stat-card h-100"><div class="card-body">
                    <div>
                        <div class="app-stat-label">Today</div>
                        <div class="app-stat-value">${stats.todayRequests ?? 0}</div>
                    </div>
                    <div class="app-stat-meta">Requests received since local midnight.</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card app-stat-card h-100"><div class="card-body">
                    <div>
                        <div class="app-stat-label">Last request</div>
                        <div class="app-stat-meta fw-semibold">${stats.lastRequestAt ? formatDate(stats.lastRequestAt) : '-'}</div>
                    </div>
                    <div class="app-stat-meta">Latest delivery touching this endpoint.</div>
                </div></div>
            </div>
            <div class="col-sm-6 col-xl-3">
                <div class="card app-stat-card h-100"><div class="card-body">
                    <div>
                        <div class="app-stat-label">Methods breakdown</div>
                        <div class="app-stat-meta fw-semibold">${renderMethodCounts(stats.methodCounts)}</div>
                    </div>
                    <div class="app-stat-meta">Traffic mix grouped by HTTP method.</div>
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
                            <dd class="col-sm-8">${renderStatusPill(webhook.isActive)}</dd>
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
                <button id="btn-clear-logs" class="btn btn-app-danger btn-sm">Clear logs</button>
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
            showNotification('Failed to copy URL', 'error');
        }
    });

    document.getElementById('btn-test-request').addEventListener('click', async () => {
        const btn = document.getElementById('btn-test-request');
        btn.disabled = true;
        btn.textContent = 'Sending...';
        try {
            const response = await fetch(webhook.endpointUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ test: true, timestamp: Date.now() })
            });
            const data = await response.json();
            showNotification(`Test sent! Request ID: ${data.requestId || 'unknown'}`, 'success');
            await renderWebhookDetail(container, webhookId);
        } catch (error) {
            showNotification('Test request failed: ' + error.message, 'error');
        } finally {
            btn.disabled = false;
            btn.textContent = 'Send Test Request';
        }
    });

    document.getElementById('btn-delete').addEventListener('click', async () => {
        showDeleteConfirmation(webhook.id, webhook.name, async () => {
            try {
                await API.deleteWebhook(webhook.id);
                showNotification('Webhook deleted', 'success');
                window.location.hash = '#dashboard';
            } catch (error) {
                showNotification('Delete failed: ' + error.message, 'error');
            }
        });
    });

    document.getElementById('btn-toggle').addEventListener('click', async () => {
        try {
            await API.toggleWebhook(webhookId);
            showNotification('Status updated', 'success');
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
                <div class="app-empty-state">No requests yet. Send a test request to populate this feed.</div>
            `;
            return;
        }

        const rows = data.items.map(log => `
            <tr>
                <td>${formatDate(log.receivedAt)}</td>
                <td><span class="app-chip">${escapeHtml(log.method)}</span></td>
                <td>${escapeHtml(log.sourceIp || '-')}</td>
                <td>${log.responseStatus === null ? '<span class="text-muted">-</span>' : `<span class="app-chip">${log.responseStatus}</span>`}</td>
                <td>${log.proxyDurationMs === null ? '<span class="text-muted">-</span>' : `${log.proxyDurationMs} ms`}</td>
                <td class="text-end"><a href="#request/${webhookId}/${log.id}" class="btn btn-outline-secondary btn-sm">Details</a></td>
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
    return entries.map(([method, count]) => `<span class="app-chip">${escapeHtml(method)} · ${count}</span>`).join(' ');
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}

function renderStatusPill(isActive) {
    return `<span class="app-status-pill ${isActive ? 'is-active' : 'is-inactive'}">${isActive ? 'Active' : 'Inactive'}</span>`;
}

function showDeleteConfirmation(webhookId, webhookName, onConfirm) {
    const modal = document.createElement('div');
    modal.className = 'modal fade show d-block';
    modal.style.backgroundColor = 'rgba(0,0,0,0.5)';
    modal.innerHTML = `
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">Delete Webhook</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">
                    <p>Are you sure you want to delete webhook <strong>${escapeHtml(webhookName)}</strong>?</p>
                    <p class="text-muted small mb-0">This action cannot be undone. All request logs will be permanently deleted.</p>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" id="modal-cancel">Cancel</button>
                    <button type="button" class="btn btn-danger" id="modal-confirm">Delete</button>
                </div>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    modal.querySelector('#modal-cancel').addEventListener('click', () => {
        modal.remove();
    });

    modal.querySelector('.btn-close').addEventListener('click', () => {
        modal.remove();
    });

    modal.querySelector('#modal-confirm').addEventListener('click', () => {
        modal.remove();
        onConfirm();
    });

    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.remove();
        }
    });
}
