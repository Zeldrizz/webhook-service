/**
 * Dashboard page - all webhooks list.
 *
 * Keeps list rendering client-side while adding explicit loading states,
 * recoverable error UI and a custom delete confirmation dialog.
 */
import API from '../api.js';
import { showNotification } from '../components/notification.js';

const PAGE_SIZE = 20;

export async function renderDashboard(container) {
    let currentPage = 0;
    let lastResponse = null;
    let searchTerm = '';
    let statusFilter = 'all';

    container.innerHTML = `
        <section class="app-page-head">
            <div>
                <div class="app-page-kicker">Operations</div>
                <h1 class="app-page-title">Webhook Dashboard</h1>
                <p class="app-page-subtitle">
                    Manage endpoints, keep delivery flows readable, and review activity without visual noise.
                </p>
            </div>
            <div class="app-actions">
                <a class="btn btn-app-primary" href="#create">New Webhook</a>
                <a class="btn btn-app-ghost" href="/swagger/" target="_blank" rel="noreferrer">API Docs</a>
            </div>
        </section>

        <section class="card mb-4">
            <div class="card-body">
                <div class="row g-3 align-items-end">
                    <div class="col-md-8">
                        <label class="form-label" for="dashboard-search">Search</label>
                        <input id="dashboard-search" class="form-control" type="search" placeholder="Name, slug, description or endpoint URL">
                    </div>
                    <div class="col-md-4">
                        <label class="form-label" for="dashboard-status">Status</label>
                        <select id="dashboard-status" class="form-select">
                            <option value="all">All</option>
                            <option value="active">Active</option>
                            <option value="inactive">Inactive</option>
                        </select>
                    </div>
                </div>
            </div>
        </section>

        <div id="dashboard-feedback" aria-live="polite"></div>
        <div id="dashboard-table"></div>
        <nav id="dashboard-pagination" class="mt-3" aria-label="Webhook pages"></nav>
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
        renderLoadingState('Loading webhooks...');
        clearFeedback();

        try {
            lastResponse = await API.fetchWebhooks(page, PAGE_SIZE);
            renderTable();
            renderPagination();
        } catch (error) {
            lastResponse = null;
            renderErrorState(error, () => loadPage(currentPage));
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
                <div class="app-empty-state">
                    No webhooks found matching your criteria.
                </div>
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
                <td>${renderStatusPill(webhook.isActive)}</td>
                <td><code class="app-code-inline">${escapeHtml(webhook.endpointUrl)}</code></td>
                <td>${formatDate(webhook.createdAt)}</td>
                <td>
                    <div class="d-flex flex-wrap gap-2">
                        <a class="btn btn-sm btn-app-ghost" href="#webhook/${escapeAttr(webhook.id)}">View</a>
                        <a class="btn btn-sm btn-app-ghost" href="#edit/${escapeAttr(webhook.id)}">Edit</a>
                        <button class="btn btn-sm btn-app-tonal" type="button" data-action="toggle" data-id="${escapeAttr(webhook.id)}">
                            ${webhook.isActive ? 'Disable' : 'Enable'}
                        </button>
                        <button class="btn btn-sm btn-app-danger" type="button" data-action="delete" data-id="${escapeAttr(webhook.id)}" data-name="${escapeAttr(webhook.name)}">
                            Delete
                        </button>
                    </div>
                </td>
            </tr>
        `).join('');

        tableContainer.innerHTML = `
            <div class="table-responsive card">
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
                await withButtonLoading(button, 'Updating...', async () => {
                    try {
                        await API.toggleWebhook(button.dataset.id);
                        showNotification('Webhook status updated', 'success');
                        await loadPage(currentPage);
                    } catch (error) {
                        showNotification(error, 'error');
                    }
                });
            });
        });

        tableContainer.querySelectorAll('[data-action="delete"]').forEach(button => {
            button.addEventListener('click', async () => {
                const confirmed = await confirmAction({
                    title: 'Delete webhook',
                    message: `Delete webhook "${button.dataset.name}"? This action cannot be undone.`,
                    confirmText: 'Delete'
                });
                if (!confirmed) {
                    return;
                }

                await withButtonLoading(button, 'Deleting...', async () => {
                    try {
                        await API.deleteWebhook(button.dataset.id);
                        showNotification('Webhook deleted', 'success');
                        const totalAfterDelete = Math.max(0, (lastResponse.total || 1) - 1);
                        const maxPage = Math.max(0, Math.ceil(totalAfterDelete / PAGE_SIZE) - 1);
                        await loadPage(Math.min(currentPage, maxPage));
                    } catch (error) {
                        showNotification(error, 'error');
                    }
                });
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

        let html = '<ul class="pagination">';
        html += renderPageItem('«', currentPage - 1, currentPage === 0);
        for (let page = 0; page < totalPages; page++) {
            html += renderPageItem(String(page + 1), page, false, page === currentPage);
        }
        html += renderPageItem('»', currentPage + 1, currentPage >= totalPages - 1);
        html += '</ul>';

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

function renderLoadingState(message) {
    document.getElementById('dashboard-table').innerHTML = `
        <div class="card">
            <div class="card-body d-flex align-items-center gap-3" role="status" aria-live="polite">
                <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                <span>${escapeHtml(message)}</span>
            </div>
        </div>
    `;
}

function renderErrorState(error, retry) {
    const feedback = document.getElementById('dashboard-feedback');
    feedback.innerHTML = `
        <div class="alert alert-danger d-flex flex-wrap justify-content-between align-items-center gap-3">
            <div>
                <div class="fw-semibold">Failed to load webhooks</div>
                <div>${escapeHtml(error?.message || String(error))}</div>
            </div>
            <button class="btn btn-sm btn-app-danger" type="button" id="dashboard-retry">Retry</button>
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

function confirmAction({ title, message, confirmText }) {
    return new Promise(resolve => {
        const modal = document.createElement('div');
        const backdrop = document.createElement('div');
        const confirmId = `confirm-${Date.now()}`;

        modal.className = 'modal fade show';
        modal.style.display = 'block';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-labelledby', `${confirmId}-title`);
        modal.innerHTML = `
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="${confirmId}-title">${escapeHtml(title)}</h5>
                        <button type="button" class="btn-close" data-confirm="cancel" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <p class="mb-0">${escapeHtml(message)}</p>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-app-ghost" data-confirm="cancel">Cancel</button>
                        <button type="button" class="btn btn-app-danger" data-confirm="ok">${escapeHtml(confirmText)}</button>
                    </div>
                </div>
            </div>
        `;

        backdrop.className = 'modal-backdrop fade show';
        document.body.classList.add('modal-open');
        document.body.append(backdrop, modal);

        const close = value => {
            modal.remove();
            backdrop.remove();
            document.body.classList.remove('modal-open');
            resolve(value);
        };

        modal.querySelector('[data-confirm="ok"]').addEventListener('click', () => close(true));
        modal.querySelectorAll('[data-confirm="cancel"]').forEach(button => {
            button.addEventListener('click', () => close(false));
        });
        backdrop.addEventListener('click', () => close(false));
    });
}

function renderPageItem(label, page, disabled = false, active = false) {
    const classes = ['page-item'];
    if (disabled) {
        classes.push('disabled');
    }
    if (active) {
        classes.push('active');
    }
    const pageAttrs = disabled
        ? 'aria-disabled="true" tabindex="-1"'
        : `data-page="${page}"`;
    return `
        <li class="${classes.join(' ')}">
            <a class="page-link" href="#" ${pageAttrs} aria-label="Page ${escapeAttr(label)}">${escapeHtml(label)}</a>
        </li>
    `;
}

function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, '&quot;');
}

function renderStatusPill(isActive) {
    return `<span class="app-status-pill ${isActive ? 'is-active' : 'is-inactive'}">${isActive ? 'Active' : 'Inactive'}</span>`;
}
