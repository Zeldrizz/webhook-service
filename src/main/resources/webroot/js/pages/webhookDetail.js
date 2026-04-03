/**
 * Webhook detail page
 * Displays configuration and request logs with pagination
 */
import API from '../api.js';
import { showNotification } from '../components/notification.js';

export async function renderWebhookDetail(container, webhookId) {
    container.innerHTML = `<div class="text-center py-4"><div class="spinner-border"></div></div>`;

    try {
        const webhook = await API.getWebhook(webhookId);
        let currentPage = 0;
        const pageSize = 20;

        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-3">
                <h2>${esc(webhook.name)}</h2>
                <div>
                    <button id="btn-copy-url" class="btn btn-outline-primary btn-sm">Copy URL</button>
                    <button id="btn-toggle" class="btn btn-sm ${webhook.isActive ? 'btn-outline-warning' : 'btn-outline-success'}">
                        ${webhook.isActive ? 'Disable' : 'Enable'}
                    </button>
                    <a href="#dashboard" class="btn btn-secondary btn-sm">Back</a>
                </div>
            </div>

            <div class="card mb-4">
                <div class="card-body">
                    <p><strong>Slug:</strong> <code>${esc(webhook.slug)}</code></p>
                    <p><strong>Endpoint:</strong> <code id="endpoint-url">${esc(webhook.endpointUrl)}</code></p>
                    <p><strong>Methods:</strong> ${esc(webhook.methods)}</p>
                    <p><strong>Status:</strong>
                        <span class="badge ${webhook.isActive ? 'bg-success' : 'bg-secondary'}">
                            ${webhook.isActive ? 'Active' : 'Inactive'}
                        </span>
                    </p>
                    ${webhook.description ? `<p><strong>Description:</strong> ${esc(webhook.description)}</p>` : ''}
                    ${webhook.proxyUrl ? `<p><strong>Proxy URL:</strong> <code>${esc(webhook.proxyUrl)}</code></p>` : ''}
                    <p><strong>Debug:</strong> ${webhook.debugMode ? 'Yes' : 'No'} |
                       <strong>Max Logs:</strong> ${webhook.maxLogCount}</p>
                </div>
            </div>

            <div class="d-flex justify-content-between align-items-center mb-2">
                <h4>Request Logs</h4>
                <button id="btn-clear-logs" class="btn btn-outline-danger btn-sm">Clear Logs</button>
            </div>

            <div id="logs-container">
                <div class="text-center py-3"><div class="spinner-border spinner-border-sm"></div></div>
            </div>
            <nav id="logs-pagination"></nav>
        `;

        document.getElementById('btn-copy-url').addEventListener('click', () => {
            navigator.clipboard.writeText(webhook.endpointUrl);
            showNotification('URL copied!', 'success');
        });

        document.getElementById('btn-toggle').addEventListener('click', async () => {
            await API.toggleWebhook(webhookId);
            renderWebhookDetail(container, webhookId);
        });

        document.getElementById('btn-clear-logs').addEventListener('click', async () => {
            if (confirm('Clear all request logs?')) {
                await API.clearRequests(webhookId);
                showNotification('Logs cleared', 'success');
                loadLogs(0);
            }
        });

        async function loadLogs(page) {
            try {
                const data = await API.fetchRequests(webhookId, page, pageSize);
                currentPage = page;
                renderLogs(data);
                renderLogsPagination(data);
            } catch (e) {
                document.getElementById('logs-container').innerHTML =
                    '<div class="alert alert-danger">Failed to load logs</div>';
            }
        }

        function renderLogs(data) {
            const el = document.getElementById('logs-container');

            if (!data.items.length) {
                el.innerHTML = '<div class="alert alert-info">No requests yet. Send a request to the endpoint URL.</div>';
                return;
            }

            const rows = data.items.map(r => `
                <tr>
                    <td>${new Date(r.receivedAt).toLocaleString()}</td>
                    <td><span class="badge bg-info">${esc(r.method)}</span></td>
                    <td>${esc(r.sourceIp || '-')}</td>
                    <td>${r.responseStatus
                        ? `<span class="badge ${r.responseStatus < 400 ? 'bg-success' : 'bg-danger'}">${r.responseStatus}</span>`
                        : '-'}
                    </td>
                    <td><a href="#request/${webhookId}/${r.id}" class="btn btn-sm btn-outline-primary">Details</a></td>
                </tr>`
            ).join('');

            el.innerHTML = `
                <table class="table table-sm table-hover">
                    <thead>
                        <tr>
                            <th>Time</th>
                            <th>Method</th>
                            <th>Source IP</th>
                            <th>Proxy Status</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>`;
        }

        function renderLogsPagination(data) {
            const totalPages = Math.ceil(data.total / data.size);
            const nav = document.getElementById('logs-pagination');

            if (totalPages <= 1) {
                nav.innerHTML = '';
                return;
            }

            let html = '<ul class="pagination pagination-sm justify-content-center">';
            for (let i = 0; i < totalPages; i++) {
                html += `<li class="page-item ${i === data.page ? 'active' : ''}">
                    <a class="page-link" href="#" data-page="${i}">${i + 1}</a></li>`;
            }
            html += '</ul>';

            nav.innerHTML = html;

            nav.querySelectorAll('.page-link').forEach(a => {
                a.addEventListener('click', e => {
                    e.preventDefault();
                    loadLogs(+a.dataset.page);
                });
            });
        }

        loadLogs(0);

    } catch (e) {
        container.innerHTML = '<div class="alert alert-danger">Webhook not found</div>';
    }
}

function esc(s) {
    const d = document.createElement('div');
    d.textContent = s || '';
    return d.innerHTML;
}
