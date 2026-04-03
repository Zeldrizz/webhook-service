/**
 * Request detail page
 * Displays full details of a received request including proxy response
 */
import API from '../api.js';
import { renderJsonViewer } from '../components/jsonViewer.js';

export async function renderRequestDetail(container, webhookId, requestId) {
    container.innerHTML = `<div class="text-center py-4"><div class="spinner-border"></div></div>`;

    try {
        const req = await API.getRequest(webhookId, requestId);

        container.innerHTML = /*html*/`
            <div class="d-flex justify-content-between align-items-center mb-3">
                <h2>Request Detail</h2>
                <a href="#webhook/${webhookId}" class="btn btn-secondary btn-sm">Back to Webhook</a>
            </div>
            <div class="card mb-3">
                <div class="card-header">Request Info</div>
                <div class="card-body">
                    <p><strong>Method:</strong> <span class="badge bg-info">${esc(req.method)}</span></p>
                    <p><strong>URL:</strong> <code>${esc(req.url)}</code></p>
                    <p><strong>Time:</strong> ${new Date(req.receivedAt).toLocaleString()}</p>
                    <p><strong>Source IP:</strong> ${esc(req.sourceIp || '-')}</p>
                    <p><strong>Content-Type:</strong> ${esc(req.contentType || '-')}</p>
                </div>
            </div>
            <div class="card mb-3">
                <div class="card-header">Headers</div>
                <div class="card-body" id="section-headers"></div>
            </div>
            <div class="card mb-3">
                <div class="card-header">Query Parameters</div>
                <div class="card-body" id="section-query"></div>
            </div>
            <div class="card mb-3">
                <div class="card-header">Body</div>
                <div class="card-body" id="section-body"></div>
            </div>
            ${req.responseStatus !== null ? `
            <div class="card mb-3">
                <div class="card-header">Proxy Response
                    <span class="badge ${req.responseStatus < 400 ? 'bg-success' : 'bg-danger'} ms-2">${req.responseStatus}</span>
                    ${req.proxyDurationMs !== null ? `<small class="text-muted ms-2">${req.proxyDurationMs}ms</small>` : ''}
                </div>
                <div class="card-body" id="section-proxy"></div>
            </div>` : ''}
        `;

        renderMapSection('section-headers', req.headers);
        renderMapSection('section-query', req.queryParams);

        const bodyEl = document.getElementById('section-body');

        if (req.body) {
            try {
                const parsed = JSON.parse(req.body);
                renderJsonViewer(bodyEl, parsed);
            } catch {
                bodyEl.innerHTML = `<pre class="mb-0">${esc(req.body)}</pre>`;
            }
        } else {
            bodyEl.innerHTML = '<span class="text-muted">Empty</span>';
        }

        if (req.responseStatus !== null) {
            const proxyEl = document.getElementById('section-proxy');

            if (req.proxyResponse) {
                try {
                    const parsed = JSON.parse(req.proxyResponse);
                    renderJsonViewer(proxyEl, parsed);
                } catch {
                    proxyEl.innerHTML = `<pre class="mb-0">${esc(req.proxyResponse)}</pre>`;
                }
            } else {
                proxyEl.innerHTML = '<span class="text-muted">Empty response</span>';
            }
        }
    } catch (e) {
        container.innerHTML = '<div class="alert alert-danger">Request not found</div>';
    }
}

function renderMapSection(elementId, map) {
    const el = document.getElementById(elementId);

    if (!map || Object.keys(map).length === 0) {
        el.innerHTML = '<span class="text-muted">None</span>';
        return;
    }

    const rows = Object.entries(map).map(([k, v]) =>
        `<tr><td class="fw-bold" style="width:30%">${esc(k)}</td><td>${esc(v)}</td></tr>`
    ).join('');

    el.innerHTML = `<table class="table table-sm mb-0"><tbody>${rows}</tbody></table>`;
}

function esc(s) {
    const d = document.createElement('div');
    d.textContent = s || '';
    return d.innerHTML;
}
