import API from '../api.js';
import { showNotification } from './notification.js';

const BADGE_UPDATE_MS = 10_000;
let badgeTimer = null;
let observerStarted = false;

function init() {
    installCacheBadge();
    installDetailFlushButtonObserver();
}

function installCacheBadge() {
    updateCacheBadge();
    if (!badgeTimer) {
        badgeTimer = window.setInterval(updateCacheBadge, BADGE_UPDATE_MS);
    }
}

async function updateCacheBadge() {
    const badge = document.getElementById('cache-badge');
    if (!badge) {
        return;
    }

    try {
        const stats = await API.getCacheStats();
        const webhookRatio = stats?.caches?.webhookBySlug?.hitRatio ?? 0;
        const templateRatio = stats?.caches?.compiledTemplate?.hitRatio ?? 0;
        badge.textContent = `Cache: ${(webhookRatio * 100).toFixed(1)}%`;
        badge.title = `webhookBySlug hit ratio: ${(webhookRatio * 100).toFixed(1)}%; compiledTemplate hit ratio: ${(templateRatio * 100).toFixed(1)}%`;
        badge.style.opacity = '1';
    } catch (error) {
        badge.textContent = 'Cache: —';
        badge.title = error?.message || String(error);
        badge.style.opacity = '0.65';
    }
}

function installDetailFlushButtonObserver() {
    if (observerStarted) {
        return;
    }
    observerStarted = true;

    const app = document.getElementById('app');
    if (!app) {
        return;
    }

    const observer = new MutationObserver(ensureFlushButton);
    observer.observe(app, { childList: true, subtree: true });
    window.addEventListener('hashchange', () => window.setTimeout(ensureFlushButton, 0));
    ensureFlushButton();
}

function ensureFlushButton() {
    if (!window.location.hash.startsWith('#webhook/')) {
        return;
    }
    if (document.getElementById('btn-flush-cache')) {
        return;
    }

    const actions = document.querySelector('.app-page-head .app-actions');
    if (!actions) {
        return;
    }

    const button = document.createElement('button');
    button.id = 'btn-flush-cache';
    button.className = 'btn btn-app-tonal';
    button.type = 'button';
    button.textContent = 'Flush cache';
    button.addEventListener('click', flushCache);

    const backLink = Array.from(actions.children).find(el => el.tagName === 'A' && el.getAttribute('href') === '#dashboard');
    if (backLink) {
        actions.insertBefore(button, backLink);
    } else {
        actions.appendChild(button);
    }
}

async function flushCache() {
    if (!window.confirm('Сбросить все кэши?')) {
        return;
    }

    const button = document.getElementById('btn-flush-cache');
    const originalText = button?.textContent;
    if (button) {
        button.disabled = true;
        button.textContent = 'Flushing...';
    }

    try {
        await API.flushCache();
        showNotification('Кэш сброшен', 'success');
        await updateCacheBadge();
    } catch (error) {
        showNotification(error?.message || String(error), 'error');
    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = originalText || 'Flush cache';
        }
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}
