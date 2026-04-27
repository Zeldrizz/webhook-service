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
        container.setAttribute('aria-live', 'polite');
        container.setAttribute('aria-atomic', 'false');
        document.body.appendChild(container);
    }
    return container;
}

export function showNotification(message, type = 'info', timeout = 3500) {
    const container = getContainer();
    const alert = document.createElement('div');
    const bootstrapType = TYPE_TO_CLASS[type] || TYPE_TO_CLASS.info;
    const text = normalizeMessage(message);

    alert.className = `alert alert-${bootstrapType} alert-dismissible fade show shadow-sm notification-toast`;
    alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
    alert.innerHTML = `
        <div class="d-flex align-items-start gap-2">
            <div class="flex-grow-1">${escapeHtml(text)}</div>
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

function normalizeMessage(message) {
    if (message instanceof Error) {
        return message.message || 'Unexpected error';
    }
    if (message && typeof message === 'object') {
        return message.message || JSON.stringify(message);
    }
    return message || 'Notification';
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}
