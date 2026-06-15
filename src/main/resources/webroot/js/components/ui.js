export function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

export function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, '&quot;');
}

export function formatDate(value) {
    return value ? new Date(value).toLocaleString() : '-';
}

export function icon(name, label = '') {
    const aria = label ? ` aria-label="${escapeAttr(label)}"` : ' aria-hidden="true"';
    return `<i class="bi bi-${escapeAttr(name)}"${aria}></i>`;
}

export function actionLabel(iconName, text) {
    return `${icon(iconName)}<span>${escapeHtml(text)}</span>`;
}

export function renderStatusBadge(isActive) {
    return `<span class="app-badge ${isActive ? 'is-success' : 'is-muted'}">${icon(isActive ? 'check-circle-fill' : 'pause-circle-fill')}${isActive ? 'Активен' : 'Выключен'}</span>`;
}

export function renderDebugBadge(debugMode) {
    return `<span class="app-badge ${debugMode ? 'is-info' : 'is-muted'}">${icon(debugMode ? 'bug-fill' : 'bug')}${debugMode ? 'Debug on' : 'Debug off'}</span>`;
}

export function renderMethodBadges(methods) {
    return parseMethods(methods)
        .map(method => `<span class="app-badge is-method">${escapeHtml(method)}</span>`)
        .join('');
}

export function parseMethods(methods) {
    return (methods || '')
        .split(',')
        .map(method => method.trim().toUpperCase())
        .filter(Boolean);
}

export function renderStatusCodeBadge(status) {
    if (status == null) {
        return '<span class="app-badge is-muted">Нет ответа</span>';
    }
    const numeric = Number(status);
    let tone = 'is-muted';
    if (numeric >= 200 && numeric < 300) tone = 'is-success';
    else if (numeric >= 300 && numeric < 400) tone = 'is-info';
    else if (numeric >= 400 && numeric < 500) tone = 'is-warning';
    else if (numeric >= 500) tone = 'is-danger';
    return `<span class="app-badge ${tone}">${escapeHtml(status)}</span>`;
}

export function renderEmptyState({ title, message, actionHtml = '' }) {
    return `
        <div class="app-empty-state">
            <div class="app-empty-icon">${icon('inbox')}</div>
            <div>
                <div class="app-empty-title">${escapeHtml(title)}</div>
                ${message ? `<div class="app-empty-text">${escapeHtml(message)}</div>` : ''}
                ${actionHtml ? `<div class="app-empty-action">${actionHtml}</div>` : ''}
            </div>
        </div>
    `;
}

export function renderSkeletonTable(columns = 5, rows = 5) {
    const cells = Array.from({ length: columns }, (_, index) => `
        <td><span class="app-skeleton ${index === 0 ? 'w-75' : 'w-50'}"></span></td>
    `).join('');
    return `
        <div class="app-panel">
            <div class="table-responsive">
                <table class="table align-middle mb-0">
                    <tbody>
                        ${Array.from({ length: rows }, () => `<tr>${cells}</tr>`).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;
}

export function renderPageItem(label, page, disabled = false, active = false) {
    const classes = ['page-item'];
    if (disabled) classes.push('disabled');
    if (active) classes.push('active');
    const pageAttrs = disabled
        ? 'aria-disabled="true" tabindex="-1"'
        : `data-page="${page}"`;
    return `
        <li class="${classes.join(' ')}">
            <a class="page-link" href="#" ${pageAttrs} aria-label="Страница ${escapeAttr(label)}">${escapeHtml(label)}</a>
        </li>
    `;
}

export async function withButtonLoading(button, label, task) {
    const originalHtml = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span>${escapeHtml(label)}</span>`;
    try {
        return await task();
    } finally {
        button.disabled = false;
        button.innerHTML = originalHtml;
    }
}

export async function copyText(value, successMessage, showNotification) {
    await navigator.clipboard.writeText(value || '');
    if (showNotification) {
        showNotification(successMessage || 'Скопировано', 'success');
    }
}

export function confirmAction({
    title,
    message,
    confirmText = 'Подтвердить',
    cancelText = 'Отмена',
    danger = false
}) {
    return new Promise(resolve => {
        const modal = document.createElement('div');
        const backdrop = document.createElement('div');
        const confirmId = `confirm-${Date.now()}`;

        modal.className = 'modal fade show app-confirm-modal';
        modal.style.display = 'block';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-labelledby', `${confirmId}-title`);
        modal.innerHTML = `
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="${confirmId}-title">${escapeHtml(title)}</h5>
                        <button type="button" class="btn-close" data-confirm="cancel" aria-label="Закрыть"></button>
                    </div>
                    <div class="modal-body">
                        <p class="mb-0">${escapeHtml(message)}</p>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-app-ghost" data-confirm="cancel">${escapeHtml(cancelText)}</button>
                        <button type="button" class="btn ${danger ? 'btn-app-danger' : 'btn-app-primary'}" data-confirm="ok">
                            ${danger ? actionLabel('trash3', confirmText) : actionLabel('check2', confirmText)}
                        </button>
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

export function setFieldError(input, message) {
    if (!input) {
        return;
    }
    input.classList.add('is-invalid');
    const target = document.querySelector(`[data-error-for="${input.id}"]`);
    if (target) {
        target.textContent = message;
    }
}

export function clearFieldErrors(root = document) {
    root.querySelectorAll('.is-invalid').forEach(input => input.classList.remove('is-invalid'));
    root.querySelectorAll('.invalid-feedback, [data-error-for]').forEach(target => {
        target.textContent = '';
    });
}

export function debounce(fn, delay = 400) {
    let timer = null;
    return (...args) => {
        window.clearTimeout(timer);
        timer = window.setTimeout(() => fn(...args), delay);
    };
}
