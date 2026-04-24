/**
 * Request detail page.
 * Displays full details of a received request including proxy response.
 */

import API from '../api.js';
import { renderJsonViewer } from '../components/jsonViewer.js';

export async function renderRequestDetail(container, webhookId, requestId) {
    container.innerHTML = `
        <div class="text-center py-5"><div class="spinner-border"></div></div>
    `;

    try {
        const requestLog = await API.getRequest(webhookId, requestId);

        container.innerHTML = `
            <div class="app-page-head">
                <div>
                    <div class="app-page-kicker">Request Trace</div>
                    <h1 class="app-page-title">Request Detail</h1>
                    <p class="app-page-subtitle">Request ID <code>${escapeHtml(requestLog.id)}</code></p>
                </div>
                <div class="app-actions">
                    <a href="#webhook/${webhookId}" class="btn btn-outline-secondary">Back to Webhook</a>
                </div>
            </div>

            <div class="card mb-4">
                <div class="card-header">Request info</div>
                <div class="card-body">
                    <div class="row g-3">
                        <div class="col-md-4"><strong>Method</strong><div><span class="app-chip">${escapeHtml(requestLog.method)}</span></div></div>
                        <div class="col-md-8"><strong>URL</strong><div><code class="app-code-inline">${escapeHtml(requestLog.url)}</code></div></div>
                        <div class="col-md-4"><strong>Received at</strong><div>${formatDate(requestLog.receivedAt)}</div></div>
                        <div class="col-md-4"><strong>Source IP</strong><div>${escapeHtml(requestLog.sourceIp || '-')}</div></div>
                        <div class="col-md-4"><strong>Content-Type</strong><div>${escapeHtml(requestLog.contentType || '-')}</div></div>
                    </div>
                </div>
            </div>

            <div class="row g-4 mb-4">
                <div class="col-lg-6">
                    <div class="card h-100">
                        <div class="card-header">Headers</div>
                        <div class="card-body"><div id="section-headers"></div></div>
                    </div>
                </div>
                <div class="col-lg-6">
                    <div class="card h-100">
                        <div class="card-header">Query parameters</div>
                        <div class="card-body"><div id="section-query"></div></div>
                    </div>
                </div>
            </div>

            <div class="card mb-4">
                <div class="card-header">Request body</div>
                <div class="card-body"><div id="section-body"></div></div>
            </div>

            ${requestLog.responseStatus !== null || requestLog.proxyResponse ? `
                <div class="card">
                    <div class="card-header d-flex justify-content-between align-items-center flex-wrap gap-2">
                        <span>Proxy response</span>
                        <span class="app-chip">${requestLog.responseStatus ?? '—'} ${requestLog.proxyDurationMs !== null ? `· ${requestLog.proxyDurationMs} ms` : ''}</span>
                    </div>
                    <div class="card-body"><div id="section-proxy"></div></div>
                </div>
            ` : ''}
        `;

        renderMapSection('section-headers', requestLog.headers);
        renderMapSection('section-query', requestLog.queryParams);
        renderContent('section-body', requestLog.body);

        if (requestLog.responseStatus !== null || requestLog.proxyResponse) {
            renderContent('section-proxy', requestLog.proxyResponse);
        }
    } catch (error) {
        container.innerHTML = `
            <div class="alert alert-danger">${escapeHtml(error.message)}</div>
        `;
    }
}

function renderMapSection(elementId, value) {
    const el = document.getElementById(elementId);
    const entries = Object.entries(value || {});

    if (!entries.length) {
        el.innerHTML = '<div class="app-empty-state">Empty</div>';
        return;
    }

    el.innerHTML = `
        <div class="table-responsive">
            <table class="table table-sm mb-0">
                <tbody>
                    ${entries.map(([key, val]) => `
                        <tr>
                            <th class="w-25">${escapeHtml(key)}</th>
                            <td><code class="app-code-inline">${escapeHtml(String(val))}</code></td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    `;
}

function renderContent(elementId, rawValue) {
    const el = document.getElementById(elementId);
    if (!el) {
        return;
    }

    if (!rawValue) {
        el.innerHTML = '<div class="app-empty-state">Empty</div>';
        return;
    }

    try {
        const parsed = JSON.parse(rawValue);
        renderJsonViewer(el, parsed);
    } catch {
        el.innerHTML = `<pre class="app-preview-box mb-0">${escapeHtml(rawValue)}</pre>`;
    }
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}
