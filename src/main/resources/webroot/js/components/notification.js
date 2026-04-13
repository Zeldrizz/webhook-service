/**
 * Toast notification component.
 * Displays success, error, info, and warning messages.
 */

const TYPE_TO_CLASS = {
    success: 'success',
    error: 'danger',
    warning: 'warning',
    info: 'info'
};

function getContainer() {
    let container = document.getElementById('notification-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'notification-container';
        container.className = 'notification-container';
        document.body.appendChild(container);
    }
    return container;
}

export function showNotification(message, type = 'info', timeout = 3500) {
    const container = getContainer();
    const alert = document.createElement('div');
    const bootstrapType = TYPE_TO_CLASS[type] || TYPE_TO_CLASS.info;

    alert.className = `alert alert-${bootstrapType} alert-dismissible fade show shadow-sm notification-toast`;
    alert.setAttribute('role', 'alert');
    alert.innerHTML = `
        <div class="d-flex align-items-start gap-2">
            <div class="flex-grow-1">${escapeHtml(message)}</div>
            <button type="button" class="btn-close" aria-label="Close"></button>
        </div>
    `;

    const closeButton = alert.querySelector('.btn-close');
    const close = () => {
        alert.classList.remove('show');
        setTimeout(() => alert.remove(), 150);
    };

    closeButton.addEventListener('click', close);
    container.appendChild(alert);

    if (timeout > 0) {
        setTimeout(close, timeout);
    }

    return alert;
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}
