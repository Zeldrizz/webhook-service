import API from '../api.js';
import { showNotification } from '../components/notification.js';

const SUPPORTED_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

export async function renderWebhookCreate(container, webhookId = null) {
    const isEdit = Boolean(webhookId);
    let existing = null;

    renderInitialLoading(container, isEdit);

    try {
        if (isEdit) {
            existing = await API.getWebhook(webhookId);
        }
    } catch (error) {
        renderLoadError(container, error);
        return;
    }

    const methods = new Set((existing?.methods || 'GET,POST').split(',').map(v => v.trim()).filter(Boolean));

    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Builder</div>
                <h1 class="app-page-title">${isEdit ? 'Edit Webhook' : 'Create Webhook'}</h1>
                <p class="app-page-subtitle">Configure the endpoint, proxy behavior and template output in one place.</p>
            </div>
            <div class="app-actions"><a class="btn btn-app-ghost" href="#dashboard">Back</a></div>
        </section>

        <div id="form-error" aria-live="polite"></div>

        <form id="webhook-form" class="row g-4" novalidate>
            <div class="col-lg-7">
                <div class="card mb-4"><div class="card-header">General</div><div class="card-body">
                    <div class="mb-3">
                        <label class="form-label" for="field-name">Name *</label>
                        <input class="form-control" id="field-name" type="text" maxlength="255" required value="${escapeAttr(existing?.name || '')}">
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="field-slug-preview">Slug preview</label>
                        <input class="form-control" id="field-slug-preview" type="text" readonly>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="field-description">Description</label>
                        <textarea class="form-control" id="field-description" rows="3">${escapeHtml(existing?.description || '')}</textarea>
                    </div>
                    <div class="mb-3">
                        <label class="form-label d-block">HTTP Methods</label>
                        <div class="d-flex flex-wrap gap-3">
                            ${SUPPORTED_METHODS.map(method => `
                                <div class="form-check">
                                    <input class="form-check-input method-checkbox" type="checkbox" id="method-${method}" value="${method}" ${methods.has(method) ? 'checked' : ''}>
                                    <label class="form-check-label" for="method-${method}">${method}</label>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                    <div class="row g-3 align-items-center">
                        <div class="col-md-6">
                            <label class="form-label" for="field-max-log-count">Max log count</label>
                            <input class="form-control" id="field-max-log-count" type="number" min="1" max="10000" value="${escapeAttr(existing?.maxLogCount || 100)}">
                        </div>
                        <div class="col-md-6"><div class="form-check mt-4">
                            <input class="form-check-input" id="field-debug-mode" type="checkbox" ${existing?.debugMode === false ? '' : 'checked'}>
                            <label class="form-check-label" for="field-debug-mode">Debug mode</label>
                        </div></div>
                    </div>
                    ${existing?.endpointUrl ? `<div class="mt-3"><a class="btn btn-sm btn-app-ghost" href="#webhook/${escapeAttr(existing.id)}">View Details</a></div>` : ''}
                </div></div>

                <div class="card mb-4"><div class="card-header">Proxy Configuration</div><div class="card-body">
                    <div class="mb-3">
                        <label class="form-label" for="field-proxy-url">Proxy URL</label>
                        <input class="form-control" id="field-proxy-url" type="url" placeholder="https://example.com/events" value="${escapeAttr(existing?.proxyUrl || '')}">
                    </div>
                    <div>
                        <label class="form-label" for="field-proxy-headers">Proxy Headers (JSON object)</label>
                        <textarea class="form-control app-code-input" id="field-proxy-headers" rows="5" spellcheck="false">${escapeHtml(existing?.proxyHeaders ? JSON.stringify(existing.proxyHeaders, null, 2) : '')}</textarea>
                    </div>
                </div></div>
            </div>

            <div class="col-lg-5">
                <div class="card mb-4"><div class="card-header">Templates</div><div class="card-body">
                    <div class="alert alert-light mb-3">
                        <div class="fw-semibold mb-1">Template syntax</div>
                        <div class="small">
                            Variables: <code>{{body.field}}</code>, <code>\${body.field}</code><br>
                            Conditions: <code>{{#if body.urgent}}...{{/if}}</code><br>
                            Loops: <code>{{#each body.items}}Name: {{name}}{{/each}}</code><br>
                            Fallback: <code>{{body.user.name | "Anonymous"}}</code>
                        </div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="field-request-template">Request Template</label>
                        <textarea class="form-control app-code-input" id="field-request-template" rows="9" spellcheck="false">${escapeHtml(existing?.requestTemplate || '')}</textarea>
                        <div class="form-text">Rendered before proxy call. Available namespaces: <code>request</code>, <code>body</code>, <code>headers</code>, <code>queryParams</code>, <code>webhook</code>.</div>
                    </div>
                    <div class="mb-3">
                        <label class="form-label" for="field-response-template">Response Template</label>
                        <textarea class="form-control app-code-input" id="field-response-template" rows="7" spellcheck="false">${escapeHtml(existing?.responseTemplate || '')}</textarea>
                        <div class="form-text">Rendered after proxy call. Available: <code>{{proxy.status}}</code>, <code>{{proxy.response}}</code>, <code>{{proxy.durationMs}}</code>.</div>
                    </div>
                    <div class="d-flex flex-wrap gap-2 mb-3">
                        <button class="btn btn-sm btn-app-tonal" type="button" id="preview-request-template">Preview Request Template</button>
                        <button class="btn btn-sm btn-app-tonal" type="button" id="preview-response-template">Preview Response Template</button>
                    </div>
                    <label class="form-label" for="template-preview-result">Template Preview</label>
                    <pre id="template-preview-result" class="app-preview-box mb-0" tabindex="0">Click Preview to see the template result.</pre>
                </div></div>

                <div class="d-flex justify-content-end gap-2">
                    <button class="btn btn-app-primary" type="submit" id="submit-webhook">${isEdit ? 'Save' : 'Create'}</button>
                    <a class="btn btn-app-ghost" href="#dashboard">Cancel</a>
                </div>
            </div>
        </form>
    `;

    const form = document.getElementById('webhook-form');
    const nameInput = document.getElementById('field-name');
    const slugPreview = document.getElementById('field-slug-preview');
    const previewBox = document.getElementById('template-preview-result');

    updateSlugPreview();
    nameInput.addEventListener('input', updateSlugPreview);

    document.getElementById('preview-request-template').addEventListener('click', async event => previewTemplate('request', event.currentTarget));
    document.getElementById('preview-response-template').addEventListener('click', async event => previewTemplate('response', event.currentTarget));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearInlineError();

        let payload;
        try {
            payload = collectPayload();
        } catch (error) {
            showInlineError(error);
            showNotification(error, 'error');
            return;
        }

        const submitButton = document.getElementById('submit-webhook');
        await withButtonLoading(submitButton, isEdit ? 'Saving...' : 'Creating...', async () => {
            setFormReadonly(form, true);
            try {
                const webhook = isEdit ? await API.updateWebhook(webhookId, payload) : await API.createWebhook(payload);
                showNotification(isEdit ? 'Webhook updated' : 'Webhook created', 'success');
                window.location.hash = `#webhook/${webhook.id}`;
            } catch (error) {
                showInlineError(error);
                showNotification(error, 'error');
            } finally {
                setFormReadonly(form, false);
            }
        });
    });

    async function previewTemplate(kind, button) {
        clearInlineError();
        await withButtonLoading(button, 'Rendering...', async () => {
            try {
                const payload = collectPayload();
                const sampleData = buildSampleData(payload, existing);
                const template = kind === 'request'
                    ? payload.requestTemplate || defaultRequestTemplate()
                    : payload.responseTemplate || defaultResponseTemplate();
                const result = await API.previewTemplate(template, sampleData);
                if (result?.ok === false) {
                    renderPreviewError(previewBox, result);
                    showInlineTemplateError(result);
                    return;
                }
                renderPreviewSuccess(previewBox, formatPreviewResult(result));
            } catch (error) {
                renderPreviewError(previewBox, { error: error?.message || String(error) });
                showInlineError(error);
                showNotification(error, 'error');
            }
        });
    }

    function collectPayload() {
        const name = document.getElementById('field-name').value.trim();
        const description = document.getElementById('field-description').value.trim();
        const proxyUrl = document.getElementById('field-proxy-url').value.trim();
        const requestTemplate = document.getElementById('field-request-template').value;
        const responseTemplate = document.getElementById('field-response-template').value;
        const maxLogCount = Number(document.getElementById('field-max-log-count').value || 100);
        const debugMode = document.getElementById('field-debug-mode').checked;
        const methods = Array.from(document.querySelectorAll('.method-checkbox:checked')).map(input => input.value);
        const proxyHeadersRaw = document.getElementById('field-proxy-headers').value.trim();

        if (!name) throw new Error('Webhook name is required');
        if (!methods.length) throw new Error('At least one HTTP method is required');
        if (!Number.isInteger(maxLogCount) || maxLogCount < 1 || maxLogCount > 10000) {
            throw new Error('maxLogCount must be an integer from 1 to 10000');
        }

        return {
            name,
            description: description || null,
            methods: methods.join(','),
            debugMode,
            proxyUrl: proxyUrl || '',
            proxyHeaders: parseProxyHeaders(proxyHeadersRaw),
            requestTemplate,
            responseTemplate,
            maxLogCount
        };
    }

    function updateSlugPreview() {
        slugPreview.value = slugify(nameInput.value);
    }
}

function renderInitialLoading(container, isEdit) {
    container.innerHTML = `
        <section class="app-page-head"><div><div class="app-page-kicker">Builder</div><h1 class="app-page-title">${isEdit ? 'Edit Webhook' : 'Create Webhook'}</h1><p class="app-page-subtitle">${isEdit ? 'Loading webhook configuration...' : 'Preparing form...'}</p></div></section>
        <div class="card"><div class="card-body d-flex align-items-center gap-3" role="status" aria-live="polite"><span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>${isEdit ? 'Loading webhook...' : 'Loading form...'}</span></div></div>
    `;
}

function renderLoadError(container, error) {
    container.innerHTML = `
        <section class="app-page-head"><div><div class="app-page-kicker">Builder</div><h1 class="app-page-title">Webhook form</h1><p class="app-page-subtitle">The form could not be loaded.</p></div><div class="app-actions"><a class="btn btn-app-ghost" href="#dashboard">Back</a></div></section>
        <div class="alert alert-danger"><div class="fw-semibold">Failed to load webhook</div><div>${escapeHtml(error?.message || String(error))}</div></div>
    `;
}

function showInlineError(error) {
    const target = document.getElementById('form-error');
    if (!target) return;
    target.innerHTML = `<div class="alert alert-danger" role="alert"><div class="fw-semibold">Action failed</div><div>${escapeHtml(error?.message || String(error))}</div></div>`;
}

function clearInlineError() {
    const target = document.getElementById('form-error');
    if (target) target.innerHTML = '';
}

function showInlineTemplateError(result) {
    const location = result.line ? `, строка ${result.line}, колонка ${result.column}` : '';
    showInlineError({ message: `Template error: ${result.error || 'syntax error'}${location}` });
}

async function withButtonLoading(button, label, task) {
    const originalHtml = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>${escapeHtml(label)}`;
    try {
        await task();
    } finally {
        button.disabled = false;
        button.innerHTML = originalHtml;
    }
}

function setFormReadonly(form, readonly) {
    form.querySelectorAll('input, textarea, select, button').forEach(control => {
        if (control.id !== 'submit-webhook') control.disabled = readonly;
    });
}

function formatPreviewResult(result) {
    const value = Object.prototype.hasOwnProperty.call(result || {}, 'result') ? result.result : result;
    return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}

function renderPreviewSuccess(previewBox, value) {
    previewBox.classList.remove('border', 'border-danger');
    previewBox.textContent = value;
}

function renderPreviewError(previewBox, result) {
    previewBox.classList.add('border', 'border-danger');
    const location = result.line ? `<br><span>строка ${escapeHtml(result.line)}, колонка ${escapeHtml(result.column)}</span>` : '';
    const snippet = result.snippet ? `<br><code>${escapeHtml(result.snippet)}</code>` : '';
    previewBox.innerHTML = `<strong>${escapeHtml(result.error || 'Template syntax error')}</strong>${location}${snippet}`;
}

function parseProxyHeaders(raw) {
    if (!raw) return {};
    let parsed;
    try {
        parsed = JSON.parse(raw);
    } catch (error) {
        throw new Error('Proxy headers must be a valid JSON object');
    }
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw new Error('Proxy headers must be a JSON object');
    }
    return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, String(value)]));
}

function defaultRequestTemplate() {
    return `{
  "event": "{{body.event | \"unknown\"}}",
  "repository": "{{body.repository.name | \"demo-repository\"}}",
  "urgentMessage": "{{#if body.urgent}}URGENT: {{body.message}}{{/if}}",
  "items": "{{#each body.items}}{{name}}={{quantity}};{{/each}}"
}`;
}

function defaultResponseTemplate() {
    return `{
  "ok": "{{#if proxy.status}}true{{/if}}",
  "status": "{{proxy.status}}",
  "traceId": "{{proxy.response.traceId | \"no-trace\"}}"
}`;
}

function buildSampleData(payload, existing) {
    const body = {
        event: 'push',
        urgent: true,
        message: 'Production deploy requested',
        repository: { name: 'demo-repository' },
        user: { id: 42, name: 'Ksenia' },
        items: [
            { name: 'build', quantity: 1, passed: true },
            { name: 'test', quantity: 3, passed: true },
            { name: 'deploy', quantity: 1, passed: false }
        ]
    };

    return {
        webhook: {
            id: existing?.id || '00000000-0000-0000-0000-000000000000',
            name: payload.name,
            slug: slugify(payload.name),
            methods: payload.methods,
            proxyUrl: payload.proxyUrl,
            proxyHeaders: payload.proxyHeaders,
            maxLogCount: payload.maxLogCount,
            debugMode: payload.debugMode
        },
        request: {
            id: '11111111-1111-1111-1111-111111111111',
            method: 'POST',
            url: '/webhook/sample',
            headers: { 'Content-Type': 'application/json', 'X-Source': 'demo' },
            queryParams: { source: 'preview' },
            body,
            rawBody: JSON.stringify(body),
            contentType: 'application/json',
            sourceIp: '127.0.0.1',
            receivedAt: new Date().toISOString()
        },
        proxy: {
            status: 200,
            response: { forwarded: true, traceId: 'abc-123', durationBucket: 'fast' },
            rawResponse: JSON.stringify({ forwarded: true, traceId: 'abc-123', durationBucket: 'fast' }),
            durationMs: 18
        },
        body,
        headers: { 'Content-Type': 'application/json', 'X-Source': 'demo' },
        queryParams: { source: 'preview' },
        responseStatus: 200,
        proxyResponse: { forwarded: true, traceId: 'abc-123', durationBucket: 'fast' },
        rawProxyResponse: JSON.stringify({ forwarded: true, traceId: 'abc-123', durationBucket: 'fast' })
    };
}

function slugify(value) {
    return (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .replace(/\s+/g, '-')
        .replace(/[^a-z0-9-]/g, '')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '') || 'webhook';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, '&quot;');
}
