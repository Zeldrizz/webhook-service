/**
 * Dashboard page - список всех вебхуков.
 */

import API from '../api.js';
import { showNotification } from '../components/notification.js';

export async function renderDashboard(container) {
    const pageSize = 20;
    let currentPage = 0;
    let lastResponse = null;
    let searchTerm = '';
    let statusFilter = 'all';

    container.innerHTML = `
        <div class="d-flex flex-column flex-lg-row justify-content-between align-items-lg-center gap-3 mb-4">
            <div>
                <h2 class="mb-1">Webhook Dashboard</h2>
                <p class="text-muted mb-0">Создание, включение, отключение и просмотр динамических вебхуков.</p>
            </div>
            <div class="d-flex gap-2">
                <a href="#create" class="btn btn-primary">Создать вебхук</a>
                <a href="/swagger" class="btn btn-outline-secondary" target="_blank" rel="noreferrer">Swagger</a>
            </div>
        </div>

        <div class="card mb-4">
            <div class="card-body">
                <div class="row g-3 align-items-end">
                    <div class="col-md-8">
                        <label class="form-label">Поиск</label>
                        <input id="dashboard-search" type="search" class="form-control" placeholder="По имени, slug или описанию">
                    </div>
                    <div class="col-md-4">
                        <label class="form-label">Статус</label>
                        <select id="dashboard-status" class="form-select">
                            <option value="all">Все</option>
                            <option value="active">Активные</option>
                            <option value="inactive">Неактивные</option>
                        </select>
                    </div>
                </div>
            </div>
        </div>

        <div class="card">
            <div class="card-body">
                <div id="dashboard-table"></div>
                <nav class="mt-3">
                    <ul id="dashboard-pagination" class="pagination pagination-sm mb-0"></ul>
                </nav>
            </div>
        </div>
    `;

    const searchInput = document.getElementById('dashboard-search');
    const statusSelect = document.getElementById('dashboard-status');

    searchInput.addEventListener('input', () => {
        searchTerm = searchInput.value.trim().toLowerCase();
        renderTable();
    });

    statusSelect.addEventListener('change', () => {
        statusFilter = statusSelect.value;
        renderTable();
    });

    await loadPage(0);

    async function loadPage(page) {
        currentPage = page;
        document.getElementById('dashboard-table').innerHTML = `
            <div class="text-center py-4"><div class="spinner-border"></div></div>
        `;

        try {
            lastResponse = await API.fetchWebhooks(page, pageSize);
            renderTable();
            renderPagination();
        } catch (error) {
            document.getElementById('dashboard-table').innerHTML = `
                <div class="alert alert-danger mb-0">${escapeHtml(error.message)}</div>
            `;
            document.getElementById('dashboard-pagination').innerHTML = '';
        }
    }

    function renderTable() {
        const tableContainer = document.getElementById('dashboard-table');
        if (!lastResponse) {
            tableContainer.innerHTML = '';
            return;
        }

        const items = filterItems(lastResponse.items);
        if (!items.length) {
            tableContainer.innerHTML = `
                <div class="alert alert-light border mb-0">Подходящие вебхуки не найдены.</div>
            `;
            return;
        }

        const rows = items.map(webhook => `
            <tr>
                <td>
                    <div class="fw-semibold">${escapeHtml(webhook.name)}</div>
                    <div class="text-muted small">${escapeHtml(webhook.slug)}</div>
                </td>
                <td><code>${escapeHtml(webhook.methods)}</code></td>
                <td>${webhook.isActive ? '<span class="badge text-bg-success">Active</span>' : '<span class="badge text-bg-secondary">Inactive</span>'}</td>
                <td><code class="app-code-inline">${escapeHtml(webhook.endpointUrl)}</code></td>
                <td>${formatDate(webhook.createdAt)}</td>
                <td>
                    <div class="btn-group btn-group-sm" role="group">
                        <a class="btn btn-outline-primary" href="#webhook/${webhook.id}">Открыть</a>
                        <a class="btn btn-outline-secondary" href="#create?id=${webhook.id}">Редактировать</a>
                        <button class="btn btn-outline-warning" data-action="toggle" data-id="${webhook.id}">${webhook.isActive ? 'Disable' : 'Enable'}</button>
                        <button class="btn btn-outline-danger" data-action="delete" data-id="${webhook.id}">Delete</button>
                    </div>
                </td>
            </tr>
        `).join('');

        tableContainer.innerHTML = `
            <div class="table-responsive">
                <table class="table table-hover align-middle mb-0">
                    <thead>
                    <tr>
                        <th>Webhook</th>
                        <th>Methods</th>
                        <th>Status</th>
                        <th>Endpoint</th>
                        <th>Created</th>
                        <th>Actions</th>
                    </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        `;

        tableContainer.querySelectorAll('[data-action="toggle"]').forEach(button => {
            button.addEventListener('click', async () => {
                try {
                    await API.toggleWebhook(button.dataset.id);
                    showNotification('Статус вебхука обновлён', 'success');
                    await loadPage(currentPage);
                } catch (error) {
                    showNotification(error.message, 'error');
                }
            });
        });

        tableContainer.querySelectorAll('[data-action="delete"]').forEach(button => {
            button.addEventListener('click', async () => {
                if (!window.confirm('Удалить этот вебхук?')) {
                    return;
                }
                try {
                    await API.deleteWebhook(button.dataset.id);
                    showNotification('Вебхук удалён', 'success');
                    const totalAfterDelete = Math.max(0, (lastResponse.total || 1) - 1);
                    const maxPage = Math.max(0, Math.ceil(totalAfterDelete / pageSize) - 1);
                    await loadPage(Math.min(currentPage, maxPage));
                } catch (error) {
                    showNotification(error.message, 'error');
                }
            });
        });
    }

    function renderPagination() {
        const nav = document.getElementById('dashboard-pagination');
        if (!lastResponse) {
            nav.innerHTML = '';
            return;
        }

        const totalPages = Math.ceil((lastResponse.total || 0) / lastResponse.size);
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
                    await loadPage(page);
                }
            });
        });
    }

    function filterItems(items) {
        return (items || []).filter(item => {
            const matchesSearch = !searchTerm || [item.name, item.slug, item.description, item.endpointUrl]
                .filter(Boolean)
                .some(value => String(value).toLowerCase().includes(searchTerm));

            const matchesStatus = statusFilter === 'all'
                || (statusFilter === 'active' && item.isActive)
                || (statusFilter === 'inactive' && !item.isActive);

            return matchesSearch && matchesStatus;
        });
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
