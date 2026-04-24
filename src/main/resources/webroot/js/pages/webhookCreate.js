/**
 * Webhook creation/edit form.
 */

import API from '../api.js';
import { showNotification } from '../components/notification.js';

const SUPPORTED_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

export async function renderWebhookCreate(container, webhookId = null) {
    const isEdit = Boolean(webhookId);
    let existing = null;

    container.innerHTML = `
        <div class="text-center py-5">
            <div class="spinner-border"></div>
        </div>
    `;

    try {
        if (isEdit) {
            existing = await API.getWebhook(webhookId);
        }
    } catch (error) {
        container.innerHTML = `
            <div class="alert alert-danger">${escapeHtml(error.message)}</div>
        `;
        return;
    }

    const methods = new Set((existing?.methods || 'GET,POST').split(',').map(v => v.trim()).filter(Boolean));

    container.innerHTML = `
        <div class="app-page-head">
            <div>
                <div class="app-page-kicker">Builder</div>
                <h1 class="app-page-title">${isEdit ? 'Edit Webhook' : 'Create Webhook'}</h1>
                <p class="app-page-subtitle">Configure the endpoint, proxy behavior and template output in one place.</p>
            </div>
            <div class="app-actions">
                <a href="${isEdit ? `#webhook/${webhookId}` : '#dashboard'}" class="btn btn-outline-secondary">Back</a>
            </div>
        </div>

        <form id="webhook-form" class="card">
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-md-8">
                        <label class="form-label">Name *</label>
                        <input id="field-name" type="text" class="form-control" required maxlength="255" value="${escapeAttr(existing?.name || '')}">
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">Slug preview</label>
                        <input id="field-slug-preview" type="text" class="form-control" readonly value="${escapeAttr(existing?.slug || '')}">
                    </div>
                    <div class="col-12">
                        <label class="form-label">Description</label>
                        <textarea id="field-description" class="form-control" rows="3" maxlength="2000">${escapeHtml(existing?.description || '')}</textarea>
                    </div>
                    <div class="col-12">
                        <label class="form-label d-block">HTTP Methods</label>
                        <div class="d-flex flex-wrap gap-3">
                            ${SUPPORTED_METHODS.map(method => `
                                <div class="form-check">
                                    <input class="form-check-input method-checkbox" type="checkbox" value="${method}" id="method-${method}" ${methods.has(method) ? 'checked' : ''}>
                                    <label class="form-check-label" for="method-${method}">${method}</label>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">Max log count</label>
                        <input id="field-max-log-count" type="number" class="form-control" min="1" max="10000" value="${escapeAttr(String(existing?.maxLogCount || 100))}">
                    </div>
                    <div class="col-md-4 d-flex align-items-end">
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox" id="field-debug-mode" ${existing?.debugMode ?? true ? 'checked' : ''}>
                            <label class="form-check-label" for="field-debug-mode">Debug mode</label>
                        </div>
                    </div>
                    <div class="col-md-4 d-flex align-items-end justify-content-md-end">
                        ${existing?.endpointUrl ? `<a href="#webhook/${existing.id}" class="btn btn-outline-secondary">View Details</a>` : ''}
                    </div>
                </div>
            </div>

            <div class="card-header">Proxy Configuration</div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-12">
                        <label class="form-label">Proxy URL</label>
                        <input id="field-proxy-url" type="url" class="form-control" placeholder="https://example.com/endpoint" value="${escapeAttr(existing?.proxyUrl || '')}">
                    </div>
                    <div class="col-12">
                        <label class="form-label">Proxy Headers (JSON object)</label>
                        <textarea id="field-proxy-headers" class="form-control app-code-input" rows="6" placeholder='{"Authorization":"Bearer ..."}'>${escapeHtml(existing?.proxyHeaders ? JSON.stringify(existing.proxyHeaders, null, 2) : '')}</textarea>
                    </div>
                </div>
            </div>

            <div class="card-header">Templates</div>
            <div class="card-body">
                <div class="row g-3">
                    <div class="col-lg-6">
                        <label class="form-label">Request Template</label>
                        <textarea id="field-request-template" class="form-control app-code-input" rows="10" placeholder='{"event":"{{body.event}}"}'>${escapeHtml(existing?.requestTemplate || '')}</textarea>
                        <div class="form-text">Supports placeholders like <code>{{body.field}}</code> and <code>${'{body.field}'}</code>.</div>
                    </div>
                    <div class="col-lg-6">
                        <label class="form-label">Response Template</label>
                        <textarea id="field-response-template" class="form-control app-code-input" rows="10" placeholder='{"ok":true,"status":"{{responseStatus}}"}'>${escapeHtml(existing?.responseTemplate || '')}</textarea>
                        <div class="form-text">Template is rendered after proxy call. Available: <code>{{proxy.status}}</code>, <code>{{proxy.response}}</code>.</div>
                    </div>
                </div>
                <div class="row g-3 mt-1">
                    <div class="col-lg-6">
                        <button id="preview-request-template" type="button" class="btn btn-outline-secondary btn-sm">Preview Request Template</button>
                    </div>
                    <div class="col-lg-6">
                        <button id="preview-response-template" type="button" class="btn btn-outline-secondary btn-sm">Preview Response Template</button>
                    </div>
                    <div class="col-12">
                        <label class="form-label">Template Preview</label>
                        <pre id="template-preview-result" class="app-preview-box mb-0">Click Preview to see the template result.</pre>
                    </div>
                </div>
            </div>

            <div class="card-body border-top d-flex gap-2 justify-content-end">
                <button type="submit" class="btn btn-primary">${isEdit ? 'Save' : 'Create'}</button>
                <a href="${isEdit ? `#webhook/${webhookId}` : '#dashboard'}" class="btn btn-outline-secondary">Cancel</a>
            </div>
        </form>
    `;

    const form = document.getElementById('webhook-form');
    const nameInput = document.getElementById('field-name');
    const slugPreview = document.getElementById('field-slug-preview');
    const previewBox = document.getElementById('template-preview-result');

    updateSlugPreview();
    nameInput.addEventListener('input', updateSlugPreview);

    document.getElementById('preview-request-template').addEventListener('click', async () => {
        await previewTemplate('request');
    });

    document.getElementById('preview-response-template').addEventListener('click', async () => {
        await previewTemplate('response');
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();

        let payload;
        try {
            payload = collectPayload();
        } catch (error) {
            showNotification(error.message, 'error');
            return;
        }

        const submitButton = form.querySelector('button[type="submit"]');
        submitButton.disabled = true;

        try {
            const webhook = isEdit
                    ? await API.updateWebhook(webhookId, payload)
                    : await API.createWebhook(payload);
            showNotification(isEdit ? 'Webhook updated' : 'Webhook created', 'success');
            window.location.hash = `#webhook/${webhook.id}`;
        } catch (error) {
            showNotification(error.message, 'error');
        } finally {
            submitButton.disabled = false;
        }
    });

    async function previewTemplate(kind) {
        try {
            const payload = collectPayload();
            const sampleData = buildSampleData(payload, existing);
            const template = kind === 'request'
                    ? payload.requestTemplate || '{"message":"request template is empty"}'
                    : payload.responseTemplate || '{"message":"response template is empty"}';
            const result = await API.previewTemplate(template, sampleData);
            previewBox.textContent = typeof result?.result === 'string'
                    ? result.result
                    : JSON.stringify(result, null, 2);
        } catch (error) {
            previewBox.textContent = error.message;
            showNotification(error.message, 'error');
        }
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

        if (!name) {
            throw new Error('Webhook name is required');
        }
        if (!methods.length) {
            throw new Error('At least one HTTP method is required');
        }
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

function parseProxyHeaders(raw) {
    if (!raw) {
        return {};
    }

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

function buildSampleData(payload, existing) {
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
            headers: {
                'Content-Type': 'application/json',
                'X-Source': 'demo'
            },
            queryParams: {
                source: 'preview'
            },
            body: {
                event: 'push',
                repository: {
                    name: 'demo-repository'
                },
                user: {
                    id: 42,
                    name: 'Ksenia'
                }
            },
            rawBody: JSON.stringify({ event: 'push', repository: { name: 'demo-repository' } }),
            contentType: 'application/json',
            sourceIp: '127.0.0.1',
            receivedAt: new Date().toISOString()
        },
        proxy: {
            status: 200,
            response: {
                forwarded: true,
                traceId: 'abc-123'
            },
            rawResponse: JSON.stringify({ forwarded: true, traceId: 'abc-123' }),
            durationMs: 18
        },
        body: {
            event: 'push',
            repository: {
                name: 'demo-repository'
            },
            user: {
                id: 42,
                name: 'Ksenia'
            }
        },
        headers: {
            'Content-Type': 'application/json',
            'X-Source': 'demo'
        },
        queryParams: {
            source: 'preview'
        },
        responseStatus: 200,
        proxyResponse: {
            forwarded: true,
            traceId: 'abc-123'
        },
        rawProxyResponse: JSON.stringify({ forwarded: true, traceId: 'abc-123' })
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
    div.textContent = value || '';
    return div.innerHTML;
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, '&quot;');
}
