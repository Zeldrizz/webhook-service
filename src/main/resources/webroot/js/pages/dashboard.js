import API from '../api.js';
import { showNotification } from '../components/notification.js';
import {
    actionLabel,
    confirmAction,
    copyText,
    escapeAttr,
    escapeHtml,
    formatDate,
    icon,
    renderDebugBadge,
    renderEmptyState,
    renderMethodBadges,
    renderPageItem,
    renderSkeletonTable,
    renderStatusBadge,
    withButtonLoading
} from '../components/ui.js';

const PAGE_SIZE = 20;

export async function renderDashboard(container) {
    const state = {
        currentPage: 0,
        lastResponse: null,
        searchTerm: '',
        statusFilter: 'all',
        debugFilter: 'all'
    };

    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Панель управления</div>
                <h1 class="app-page-title">Вебхуки</h1>
            </div>
            <div class="app-actions">
                <a class="btn btn-app-primary" href="#create">${actionLabel('plus-lg', 'Создать')}</a>
                <a class="btn btn-app-ghost" href="/swagger/" target="_blank" rel="noreferrer">${actionLabel('file-earmark-code', 'OpenAPI')}</a>
            </div>
        </section>

        <section class="app-toolbar" aria-label="Фильтры вебхуков">
            <div class="app-toolbar-search">
                <label class="form-label" for="dashboard-search">Поиск</label>
                <div class="app-input-icon">
                    ${icon('search')}
                    <input id="dashboard-search" class="form-control" type="search" placeholder="Название, slug, описание или endpoint">
                </div>
            </div>
            <div>
                <label class="form-label" for="dashboard-status">Статус</label>
                <select id="dashboard-status" class="form-select">
                    <option value="all">Все</option>
                    <option value="active">Активные</option>
                    <option value="inactive">Выключенные</option>
                </select>
            </div>
            <div>
                <label class="form-label" for="dashboard-debug">Debug</label>
                <select id="dashboard-debug" class="form-select">
                    <option value="all">Любой</option>
                    <option value="on">Debug on</option>
                    <option value="off">Debug off</option>
                </select>
            </div>
        </section>

        <div id="dashboard-feedback" aria-live="polite"></div>
        <div id="dashboard-summary" class="app-list-summary"></div>
        <div id="dashboard-table"></div>
        <nav id="dashboard-pagination" class="mt-3" aria-label="Страницы вебхуков"></nav>
    `;

    document.getElementById('dashboard-search').addEventListener('input', event => {
        state.searchTerm = event.target.value.trim().toLowerCase();
        renderTable();
    });
    document.getElementById('dashboard-status').addEventListener('change', event => {
        state.statusFilter = event.target.value;
        renderTable();
    });
    document.getElementById('dashboard-debug').addEventListener('change', event => {
        state.debugFilter = event.target.value;
        renderTable();
    });

    await loadPage(0);

    async function loadPage(page) {
        state.currentPage = page;
        renderLoadingState();
        clearFeedback();

        try {
            state.lastResponse = await API.fetchWebhooks(page, PAGE_SIZE);
            renderTable();
            renderPagination();
        } catch (error) {
            state.lastResponse = null;
            renderErrorState(error, () => loadPage(state.currentPage));
            document.getElementById('dashboard-summary').innerHTML = '';
            document.getElementById('dashboard-pagination').innerHTML = '';
        }
    }

    function renderTable() {
        const tableContainer = document.getElementById('dashboard-table');
        const summary = document.getElementById('dashboard-summary');
        if (!state.lastResponse) {
            tableContainer.innerHTML = '';
            summary.innerHTML = '';
            return;
        }

        const originalItems = state.lastResponse.items || [];
        const items = filterItems(originalItems);
        const hasFilters = Boolean(state.searchTerm) || state.statusFilter !== 'all' || state.debugFilter !== 'all';
        summary.innerHTML = renderSummary(items.length, state.lastResponse.total || 0, hasFilters);

        if (!items.length) {
            tableContainer.innerHTML = hasFilters
                ? renderEmptyState({
                    title: 'Ничего не найдено',
                    message: 'Попробуйте изменить поиск или сбросить фильтры.'
                })
                : renderEmptyState({
                    title: 'Создайте первый вебхук',
                    message: 'После создания здесь появятся endpoint URL, статусы и быстрые действия.',
                    actionHtml: `<a class="btn btn-app-primary" href="#create">${actionLabel('plus-lg', 'Создать вебхук')}</a>`
                });
            return;
        }

        const rows = items.map(webhook => `
            <tr>
                <td>
                    <div class="app-row-title">${escapeHtml(webhook.name)}</div>
                    <div class="app-row-subtitle">${escapeHtml(webhook.slug)}</div>
                </td>
                <td>
                    <div class="app-badge-stack">
                        ${renderStatusBadge(webhook.isActive)}
                        ${renderDebugBadge(webhook.debugMode)}
                    </div>
                </td>
                <td>
                    <div class="app-endpoint-copy">
                        <code class="app-code-inline">${escapeHtml(webhook.endpointUrl)}</code>
                        <button class="btn btn-icon btn-app-ghost" type="button" title="Скопировать endpoint" data-action="copy" data-url="${escapeAttr(webhook.endpointUrl)}">
                            ${icon('clipboard')}
                        </button>
                    </div>
                </td>
                <td><div class="app-badge-stack">${renderMethodBadges(webhook.methods)}</div></td>
                <td>${formatDate(webhook.createdAt)}</td>
                <td>
                    <div class="app-row-actions">
                        <a class="btn btn-icon btn-app-ghost" title="Открыть" href="#webhook/${escapeAttr(webhook.id)}">${icon('box-arrow-up-right')}</a>
                        <a class="btn btn-icon btn-app-ghost" title="Редактировать" href="#edit/${escapeAttr(webhook.id)}">${icon('pencil')}</a>
                        <button class="btn btn-icon btn-app-tonal" type="button" title="${webhook.isActive ? 'Выключить' : 'Включить'}" data-action="toggle" data-id="${escapeAttr(webhook.id)}">
                            ${icon(webhook.isActive ? 'pause-fill' : 'play-fill')}
                        </button>
                        <button class="btn btn-icon btn-app-danger" type="button" title="Удалить" data-action="delete" data-id="${escapeAttr(webhook.id)}" data-name="${escapeAttr(webhook.name)}">
                            ${icon('trash3')}
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

        tableContainer.innerHTML = `
            <div class="app-panel">
                <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0">
                        <thead>
                            <tr>
                                <th>Вебхук</th>
                                <th>Состояние</th>
                                <th>Endpoint</th>
                                <th>Методы</th>
                                <th>Создан</th>
                                <th class="text-end">Действия</th>
                            </tr>
                        </thead>
                        <tbody>${rows}</tbody>
                    </table>
                </div>
            </div>
        `;

        tableContainer.querySelectorAll('[data-action="copy"]').forEach(button => {
            button.addEventListener('click', async () => {
                try {
                    await copyText(button.dataset.url, 'Endpoint URL скопирован', showNotification);
                } catch {
                    showNotification('Не удалось скопировать URL', 'error');
                }
            });
        });

        tableContainer.querySelectorAll('[data-action="toggle"]').forEach(button => {
            button.addEventListener('click', async () => {
                await withButtonLoading(button, '', async () => {
                    try {
                        await API.toggleWebhook(button.dataset.id);
                        showNotification('Статус вебхука обновлён', 'success');
                        await loadPage(state.currentPage);
                    } catch (error) {
                        showNotification(error, 'error');
                    }
                });
            });
        });

        tableContainer.querySelectorAll('[data-action="delete"]').forEach(button => {
            button.addEventListener('click', async () => {
                const confirmed = await confirmAction({
                    title: 'Удалить вебхук',
                    message: `Удалить вебхук "${button.dataset.name}"? История запросов тоже будет удалена.`,
                    confirmText: 'Удалить',
                    danger: true
                });
                if (!confirmed) {
                    return;
                }

                await withButtonLoading(button, '', async () => {
                    try {
                        await API.deleteWebhook(button.dataset.id);
                        showNotification('Вебхук удалён', 'success');
                        const totalAfterDelete = Math.max(0, (state.lastResponse.total || 1) - 1);
                        const maxPage = Math.max(0, Math.ceil(totalAfterDelete / PAGE_SIZE) - 1);
                        await loadPage(Math.min(state.currentPage, maxPage));
                    } catch (error) {
                        showNotification(error, 'error');
                    }
                });
            });
        });
    }

    function renderPagination() {
        const nav = document.getElementById('dashboard-pagination');
        if (!state.lastResponse) {
            nav.innerHTML = '';
            return;
        }

        const totalPages = Math.ceil((state.lastResponse.total || 0) / state.lastResponse.size);
        if (totalPages <= 1) {
            nav.innerHTML = '';
            return;
        }

        let html = '<ul class="pagination">';
        html += renderPageItem('Назад', state.currentPage - 1, state.currentPage === 0);
        for (let page = 0; page < totalPages; page++) {
            html += renderPageItem(String(page + 1), page, false, page === state.currentPage);
        }
        html += renderPageItem('Вперёд', state.currentPage + 1, state.currentPage >= totalPages - 1);
        html += '</ul>';

        nav.innerHTML = html;
        nav.querySelectorAll('[data-page]').forEach(link => {
            link.addEventListener('click', async event => {
                event.preventDefault();
                const page = Number(link.dataset.page);
                if (Number.isFinite(page) && page !== state.currentPage) {
                    await loadPage(page);
                }
            });
        });
    }

    function filterItems(items) {
        return (items || []).filter(item => {
            const matchesSearch = !state.searchTerm || [item.name, item.slug, item.description, item.endpointUrl]
                .filter(Boolean)
                .some(value => String(value).toLowerCase().includes(state.searchTerm));
            const matchesStatus = state.statusFilter === 'all'
                || (state.statusFilter === 'active' && item.isActive)
                || (state.statusFilter === 'inactive' && !item.isActive);
            const matchesDebug = state.debugFilter === 'all'
                || (state.debugFilter === 'on' && item.debugMode)
                || (state.debugFilter === 'off' && !item.debugMode);
            return matchesSearch && matchesStatus && matchesDebug;
        });
    }
}

function renderLoadingState() {
    document.getElementById('dashboard-table').innerHTML = renderSkeletonTable(6, 6);
}

function renderErrorState(error, retry) {
    const feedback = document.getElementById('dashboard-feedback');
    feedback.innerHTML = `
        <div class="alert alert-danger app-alert-row">
            <div>
                <div class="fw-semibold">Не удалось загрузить вебхуки</div>
                <div>${escapeHtml(error?.message || String(error))}</div>
            </div>
            <button class="btn btn-sm btn-app-danger" type="button" id="dashboard-retry">${actionLabel('arrow-clockwise', 'Повторить')}</button>
        </div>
    `;
    document.getElementById('dashboard-table').innerHTML = '';
    document.getElementById('dashboard-retry').addEventListener('click', retry);
}

function clearFeedback() {
    const feedback = document.getElementById('dashboard-feedback');
    if (feedback) {
        feedback.innerHTML = '';
    }
}

function renderSummary(visible, total, hasFilters) {
    const filterText = hasFilters ? `, показано ${visible}` : '';
    return `${icon('collection')} Всего: ${escapeHtml(total)}${escapeHtml(filterText)}`;
}
