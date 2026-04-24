/**
 * Main application entry point.
 * Handles SPA routing and initialization.
 */

import { renderDashboard } from './pages/dashboard.js';
import { renderWebhookCreate } from './pages/webhookCreate.js';
import { renderWebhookDetail } from './pages/webhookDetail.js';
import { renderRequestDetail } from './pages/requestDetail.js';

const app = document.getElementById('app');

function getRoute() {
    const rawHash = window.location.hash || '#dashboard';
    const hash = rawHash.startsWith('#') ? rawHash.slice(1) : rawHash;
    const [pathPart, queryString = ''] = hash.split('?');
    const segments = pathPart.split('/').filter(Boolean);
    const query = new URLSearchParams(queryString);

    return {
        rawHash,
        segments,
        query
    };
}

async function route() {
    const { segments, query } = getRoute();

    if (!segments.length || segments[0] === 'dashboard') {
        await renderDashboard(app);
        return;
    }

    if (segments[0] === 'create') {
        await renderWebhookCreate(app, query.get('id'));
        return;
    }

    if (segments[0] === 'edit' && segments[1]) {
        await renderWebhookCreate(app, segments[1]);
        return;
    }

    if (segments[0] === 'webhook' && segments[1]) {
        await renderWebhookDetail(app, segments[1]);
        return;
    }

    if (segments[0] === 'request' && segments[1] && segments[2]) {
        await renderRequestDetail(app, segments[1], segments[2]);
        return;
    }

    app.innerHTML = `
        <div class="alert alert-warning">
            <h4 class="alert-heading">Page Not Found</h4>
            <p class="mb-0">Check the hash route or return to the <a href="#dashboard" class="alert-link">dashboard</a>.</p>
        </div>
    `;
}

window.addEventListener('hashchange', () => {
    route().catch(renderFatalError);
});

window.addEventListener('DOMContentLoaded', () => {
    if (!window.location.hash) {
        window.location.hash = '#dashboard';
        return;
    }
    route().catch(renderFatalError);
});

function renderFatalError(error) {
    console.error(error);
    app.innerHTML = `
        <div class="alert alert-danger">
            <h4 class="alert-heading">Interface Load Error</h4>
            <pre class="mb-0 app-pre-wrap">${escapeHtml(error?.message || String(error))}</pre>
        </div>
    `;
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}

console.log('Webhook Service App initialized');
