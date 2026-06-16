import API from '../api.js';
import { showNotification } from './notification.js';
import { confirmAction, withButtonLoading } from './ui.js';

const BADGE_UPDATE_MS = 10_000;
let badgeTimer = null;

function init() {
    updateCacheBadge();
    if (!badgeTimer) {
        badgeTimer = window.setInterval(updateCacheBadge, BADGE_UPDATE_MS);
    }
}

export async function updateCacheBadge() {
    const badge = document.getElementById('cache-badge');
    if (!badge) {
        return;
    }

    try {
        const stats = await API.getCacheStats();
        const webhookRatio = stats?.caches?.webhookBySlug?.hitRatio ?? 0;
        const templateRatio = stats?.caches?.compiledTemplate?.hitRatio ?? 0;
        const size = stats?.caches?.webhookBySlug?.size ?? 0;
        badge.textContent = `Cache hit ${(webhookRatio * 100).toFixed(1)}%`;
        badge.title = `Hit ratio показывает долю запросов, обслуженных из кэша. webhookBySlug: ${(webhookRatio * 100).toFixed(1)}%, compiledTemplate: ${(templateRatio * 100).toFixed(1)}%, webhook cache size: ${size}.`;
        badge.classList.remove('is-muted');
    } catch (error) {
        badge.textContent = 'Кэш —';
        badge.title = error?.message || String(error);
        badge.classList.add('is-muted');
    }
}

export async function flushCacheWithConfirmation(button = null) {
    const confirmed = await confirmAction({
        title: 'Сбросить кэш',
        message: 'Все локальные кэши будут очищены на инстансах сервиса. Следующие запросы прогреют их заново.',
        confirmText: 'Сбросить',
        danger: true
    });
    if (!confirmed) {
        return;
    }

    const run = async () => {
        await API.flushCache();
        showNotification('Кэш сброшен', 'success');
        await updateCacheBadge();
    };

    try {
        if (button) {
            await withButtonLoading(button, 'Сбрасываю...', run);
        } else {
            await run();
        }
    } catch (error) {
        showNotification(error?.message || String(error), 'error');
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
