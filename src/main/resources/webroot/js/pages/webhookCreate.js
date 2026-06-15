import API from '../api.js';
import { showNotification } from '../components/notification.js';
import {
    actionLabel,
    clearFieldErrors,
    debounce,
    escapeAttr,
    escapeHtml,
    icon,
    setFieldError,
    withButtonLoading
} from '../components/ui.js';

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
                <div class="app-page-kicker">${isEdit ? 'Редактирование' : 'Новый endpoint'}</div>
                <h1 class="app-page-title">${isEdit ? escapeHtml(existing.name) : 'Создать вебхук'}</h1>
                <p class="app-page-subtitle">Настройте приём, проксирование и шаблоны ответа без длинной простыни полей.</p>
            </div>
            <div class="app-actions">
                ${existing?.endpointUrl ? `<a class="btn btn-app-ghost" href="#webhook/${escapeAttr(existing.id)}">${actionLabel('box-arrow-up-right', 'К деталям')}</a>` : ''}
                <a class="btn btn-app-ghost" href="#dashboard">${actionLabel('arrow-left', 'Назад')}</a>
            </div>
        </section>

        <div id="form-error" aria-live="polite"></div>

        <form id="webhook-form" class="app-form" novalidate>
            <section class="app-form-section">
                <div class="app-section-head">
                    <div>
                        <h2>Основное</h2>
                        <p>Название, публичный slug, методы и режим логирования.</p>
                    </div>
                    <div class="app-section-counter">1</div>
                </div>
                <div class="row g-3">
                    <div class="col-lg-6">
                        <label class="form-label" for="field-name">Название *</label>
                        <input class="form-control" id="field-name" type="text" maxlength="255" required placeholder="GitHub Push Events" value="${escapeAttr(existing?.name || '')}">
                        <div class="invalid-feedback" data-error-for="field-name"></div>
                    </div>
                    <div class="col-lg-6">
                        <label class="form-label" for="field-slug-preview">Slug preview</label>
                        <div class="app-input-icon">
                            ${icon('link-45deg')}
                            <input class="form-control" id="field-slug-preview" type="text" readonly>
                        </div>
                        <div class="form-text">Slug генерируется из названия и используется в <code>/webhook/:slug</code>.</div>
                    </div>
                    <div class="col-12">
                        <label class="form-label" for="field-description">Описание</label>
                        <textarea class="form-control" id="field-description" rows="3" placeholder="Куда приходит событие и кто им пользуется">${escapeHtml(existing?.description || '')}</textarea>
                    </div>
                    <div class="col-lg-7">
                        <label class="form-label d-block">HTTP методы *</label>
                        <div class="app-segmented" role="group" aria-label="HTTP методы">
                            ${SUPPORTED_METHODS.map(method => `
                                <input class="btn-check method-checkbox" type="checkbox" id="method-${method}" value="${method}" ${methods.has(method) ? 'checked' : ''}>
                                <label class="btn" for="method-${method}">${escapeHtml(method)}</label>
                            `).join('')}
                        </div>
                        <div class="invalid-feedback d-block" id="method-error"></div>
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label" for="field-max-log-count">Лимит логов</label>
                        <input class="form-control" id="field-max-log-count" type="number" min="1" max="10000" value="${escapeAttr(existing?.maxLogCount || 100)}">
                        <div class="invalid-feedback" data-error-for="field-max-log-count"></div>
                    </div>
                    <div class="col-sm-6 col-lg-3">
                        <label class="form-label d-block">Debug</label>
                        <div class="app-toggle-line">
                            <input class="form-check-input" id="field-debug-mode" type="checkbox" ${existing?.debugMode === false ? '' : 'checked'}>
                            <label class="form-check-label" for="field-debug-mode">Сохранять историю запросов</label>
                        </div>
                    </div>
                </div>
            </section>

            <section class="app-form-section">
                <div class="app-section-head">
                    <div>
                        <h2>Проксирование</h2>
                        <p>Опциональный upstream и заголовки, которые сервис добавит при forward.</p>
                    </div>
                    <div class="app-section-counter">2</div>
                </div>
                <div class="row g-3">
                    <div class="col-lg-6">
                        <label class="form-label" for="field-proxy-url">Proxy URL</label>
                        <input class="form-control" id="field-proxy-url" type="url" placeholder="https://example.com/events" value="${escapeAttr(existing?.proxyUrl || '')}">
                        <div class="invalid-feedback" data-error-for="field-proxy-url"></div>
                        <div class="form-text">Если оставить пустым, запрос будет принят без внешнего forward.</div>
                    </div>
                    <div class="col-lg-6">
                        <label class="form-label" for="field-proxy-headers">Proxy headers</label>
                        <textarea class="form-control app-code-input" id="field-proxy-headers" rows="5" spellcheck="false" placeholder='{"X-Service": "webhook-demo"}'>${escapeHtml(existing?.proxyHeaders ? JSON.stringify(existing.proxyHeaders, null, 2) : '')}</textarea>
                        <div class="invalid-feedback" data-error-for="field-proxy-headers"></div>
                        <div class="form-text">JSON object; значения будут отправлены как строки.</div>
                    </div>
                </div>
            </section>

            <section class="app-form-section">
                <div class="app-section-head">
                    <div>
                        <h2>Шаблоны</h2>
                        <p>Preview использует демо-данные и показывает ошибку синтаксиса рядом с редактором.</p>
                    </div>
                    <div class="app-section-counter">3</div>
                </div>

                <div class="app-template-help">
                    <span><code>{{body.field}}</code> или <code>\${body.field}</code></span>
                    <span><code>{{#if body.urgent}}...{{else}}...{{/if}}</code></span>
                    <span><code>{{#each body.items}}{{name}}{{/each}}</code></span>
                    <span><code>{{body.user.name | "Anonymous"}}</code></span>
                </div>

                <div class="app-template-grid">
                    <div>
                        <div class="app-field-head">
                            <label class="form-label" for="field-request-template">Request template</label>
                            <button class="btn btn-sm btn-app-tonal" type="button" id="preview-request-template">${actionLabel('play-circle', 'Preview')}</button>
                        </div>
                        <textarea class="form-control app-code-input" id="field-request-template" rows="12" spellcheck="false" placeholder='{"event":"{{body.event | \"unknown\"}}"}'>${escapeHtml(existing?.requestTemplate || '')}</textarea>
                        <div class="form-text">Рендерится перед proxy-вызовом. Доступны <code>request</code>, <code>body</code>, <code>headers</code>, <code>queryParams</code>, <code>webhook</code>.</div>
                    </div>
                    <div>
                        <label class="form-label" for="request-template-preview">Request preview</label>
                        <pre id="request-template-preview" class="app-preview-box" tabindex="0">Нажмите Preview или начните вводить шаблон.</pre>
                    </div>
                </div>

                <div class="app-template-grid">
                    <div>
                        <div class="app-field-head">
                            <label class="form-label" for="field-response-template">Response template</label>
                            <button class="btn btn-sm btn-app-tonal" type="button" id="preview-response-template">${actionLabel('play-circle', 'Preview')}</button>
                        </div>
                        <textarea class="form-control app-code-input" id="field-response-template" rows="10" spellcheck="false" placeholder='{"ok":true,"status":"{{proxy.status}}"}'>${escapeHtml(existing?.responseTemplate || '')}</textarea>
                        <div class="form-text">Рендерится после proxy-вызова. Доступны <code>proxy.status</code>, <code>proxy.response</code>, <code>proxy.durationMs</code>.</div>
                    </div>
                    <div>
                        <label class="form-label" for="response-template-preview">Response preview</label>
                        <pre id="response-template-preview" class="app-preview-box" tabindex="0">Нажмите Preview или начните вводить шаблон.</pre>
                    </div>
                </div>
            </section>

            <div class="app-form-footer">
                <a class="btn btn-app-ghost" href="#dashboard">${actionLabel('x-lg', 'Отмена')}</a>
                <button class="btn btn-app-primary" type="submit" id="submit-webhook">${actionLabel(isEdit ? 'check2-circle' : 'plus-circle', isEdit ? 'Сохранить' : 'Создать')}</button>
            </div>
        </form>
    `;

    const form = document.getElementById('webhook-form');
    const nameInput = document.getElementById('field-name');
    const slugPreview = document.getElementById('field-slug-preview');
    const requestTemplate = document.getElementById('field-request-template');
    const responseTemplate = document.getElementById('field-response-template');

    updateSlugPreview();
    nameInput.addEventListener('input', updateSlugPreview);

    document.getElementById('field-proxy-headers').addEventListener('blur', () => {
        clearFieldErrors(form);
        try {
            collectPayload({ validate: false, parseHeaders: true });
        } catch (error) {
            showFieldValidationError(error);
        }
    });

    document.getElementById('preview-request-template').addEventListener('click', event => previewTemplate('request', event.currentTarget, false));
    document.getElementById('preview-response-template').addEventListener('click', event => previewTemplate('response', event.currentTarget, false));

    const requestPreviewDebounced = debounce(() => {
        if (requestTemplate.value.trim()) previewTemplate('request', null, true);
    }, 700);
    const responsePreviewDebounced = debounce(() => {
        if (responseTemplate.value.trim()) previewTemplate('response', null, true);
    }, 700);
    requestTemplate.addEventListener('input', requestPreviewDebounced);
    responseTemplate.addEventListener('input', responsePreviewDebounced);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearFieldErrors(form);
        clearInlineError();

        let payload;
        try {
            payload = collectPayload({ validate: true, parseHeaders: true });
        } catch (error) {
            showFieldValidationError(error);
            showNotification(error, 'error');
            return;
        }

        const submitButton = document.getElementById('submit-webhook');
        await withButtonLoading(submitButton, isEdit ? 'Сохраняю...' : 'Создаю...', async () => {
            setFormReadonly(form, true);
            try {
                const webhook = isEdit ? await API.updateWebhook(webhookId, payload) : await API.createWebhook(payload);
                showNotification(isEdit ? 'Вебхук обновлён' : 'Вебхук создан', 'success');
                window.location.hash = `#webhook/${webhook.id}`;
            } catch (error) {
                showInlineError(error);
                showNotification(error, 'error');
            } finally {
                setFormReadonly(form, false);
            }
        });
    });

    async function previewTemplate(kind, button, silent) {
        const previewBox = document.getElementById(`${kind}-template-preview`);
        clearInlineError();
        previewBox.classList.remove('is-error');

        const run = async () => {
            try {
                const payload = collectPayload({ validate: false, parseHeaders: true });
                const template = kind === 'request'
                    ? payload.requestTemplate || defaultRequestTemplate()
                    : payload.responseTemplate || defaultResponseTemplate();
                if (!template.trim()) {
                    previewBox.textContent = 'Шаблон пустой. Будет использовано исходное тело/ответ.';
                    return;
                }
                const result = await API.previewTemplate(template, buildSampleData(payload, existing));
                if (result?.ok === false) {
                    renderPreviewError(previewBox, result);
                    if (!silent) showInlineTemplateError(result);
                    return;
                }
                renderPreviewSuccess(previewBox, formatPreviewResult(result));
            } catch (error) {
                renderPreviewError(previewBox, { error: error?.message || String(error) });
                if (!silent) {
                    showFieldValidationError(error);
                    showNotification(error, 'error');
                }
            }
        };

        if (button) {
            await withButtonLoading(button, 'Рендерю...', run);
        } else {
            await run();
        }
    }

    function collectPayload({ validate, parseHeaders }) {
        const name = document.getElementById('field-name').value.trim();
        const description = document.getElementById('field-description').value.trim();
        const proxyUrl = document.getElementById('field-proxy-url').value.trim();
        const requestTemplateValue = requestTemplate.value;
        const responseTemplateValue = responseTemplate.value;
        const maxLogCountRaw = document.getElementById('field-max-log-count').value || '100';
        const maxLogCount = Number(maxLogCountRaw);
        const debugMode = document.getElementById('field-debug-mode').checked;
        const selectedMethods = Array.from(document.querySelectorAll('.method-checkbox:checked')).map(input => input.value);
        const proxyHeadersRaw = document.getElementById('field-proxy-headers').value.trim();

        if (validate && !name) {
            throw validationError('field-name', 'Введите понятное название вебхука.');
        }
        if (validate && !selectedMethods.length) {
            throw validationError('methods', 'Выберите хотя бы один HTTP метод.');
        }
        if (!Number.isInteger(maxLogCount) || maxLogCount < 1 || maxLogCount > 10000) {
            throw validationError('field-max-log-count', 'Лимит должен быть целым числом от 1 до 10000.');
        }
        if (proxyUrl) {
            try {
                new URL(proxyUrl);
            } catch {
                throw validationError('field-proxy-url', 'Введите полный URL, например https://example.com/events.');
            }
        }

        return {
            name: name || 'Webhook preview',
            description: description || null,
            methods: selectedMethods.join(','),
            debugMode,
            proxyUrl: proxyUrl || '',
            proxyHeaders: parseHeaders ? parseProxyHeaders(proxyHeadersRaw) : {},
            requestTemplate: requestTemplateValue,
            responseTemplate: responseTemplateValue,
            maxLogCount
        };
    }

    function updateSlugPreview() {
        slugPreview.value = slugify(nameInput.value);
    }
}

function renderInitialLoading(container, isEdit) {
    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Форма</div>
                <h1 class="app-page-title">${isEdit ? 'Редактирование вебхука' : 'Создание вебхука'}</h1>
                <p class="app-page-subtitle">${isEdit ? 'Загружаю настройки...' : 'Готовлю форму...'}</p>
            </div>
        </section>
        <div class="app-panel app-loading-line" role="status" aria-live="polite">
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
            <span>${isEdit ? 'Загружаю вебхук...' : 'Загружаю форму...'}</span>
        </div>
    `;
}

function renderLoadError(container, error) {
    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Форма</div>
                <h1 class="app-page-title">Вебхук</h1>
                <p class="app-page-subtitle">Не удалось загрузить настройки.</p>
            </div>
            <div class="app-actions"><a class="btn btn-app-ghost" href="#dashboard">${actionLabel('arrow-left', 'Назад')}</a></div>
        </section>
        <div class="alert alert-danger"><div class="fw-semibold">Ошибка загрузки</div><div>${escapeHtml(error?.message || String(error))}</div></div>
    `;
}

function showInlineError(error) {
    const target = document.getElementById('form-error');
    if (!target) return;
    target.innerHTML = `<div class="alert alert-danger" role="alert"><div class="fw-semibold">Действие не выполнено</div><div>${escapeHtml(error?.message || String(error))}</div></div>`;
}

function clearInlineError() {
    const target = document.getElementById('form-error');
    if (target) target.innerHTML = '';
}

function showInlineTemplateError(result) {
    const location = result.line ? `, строка ${result.line}, колонка ${result.column}` : '';
    showInlineError({ message: `Ошибка шаблона: ${result.error || 'syntax error'}${location}` });
}

function showFieldValidationError(error) {
    if (error?.fieldId === 'methods') {
        document.getElementById('method-error').textContent = error.message;
        return;
    }
    if (error?.fieldId) {
        setFieldError(document.getElementById(error.fieldId), error.message);
    } else {
        showInlineError(error);
    }
}

function validationError(fieldId, message) {
    const error = new Error(message);
    error.fieldId = fieldId;
    return error;
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
    previewBox.classList.remove('is-error');
    previewBox.textContent = value;
}

function renderPreviewError(previewBox, result) {
    previewBox.classList.add('is-error');
    const location = result.line ? `\nстрока ${result.line}, колонка ${result.column}` : '';
    const snippet = result.snippet ? `\n${result.snippet}` : '';
    previewBox.textContent = `${result.error || 'Template syntax error'}${location}${snippet}`;
}

function parseProxyHeaders(raw) {
    if (!raw) return {};
    let parsed;
    try {
        parsed = JSON.parse(raw);
    } catch {
        throw validationError('field-proxy-headers', 'Headers должны быть валидным JSON object.');
    }
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw validationError('field-proxy-headers', 'Headers должны быть JSON object, не массивом.');
    }
    return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, String(value)]));
}

function defaultRequestTemplate() {
    return `{
  "event": "{{body.event | \"unknown\"}}",
  "repository": "{{body.repository.name | \"demo-repository\"}}",
  "urgentMessage": "{{#if body.urgent}}URGENT: {{body.message}}{{else}}normal{{/if}}",
  "items": "{{#each body.items}}{{@index}}:{{name}}={{quantity}};{{/each}}"
}`;
}

function defaultResponseTemplate() {
    return `{
  "ok": "{{#if proxy.status}}true{{else}}false{{/if}}",
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
