/**
 * HTTP client for backend API.
 * Provides methods for all API endpoints.
 */

const API = {
    baseUrl: '/api',

    async request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has('Content-Type')) {
            headers.set('Content-Type', 'application/json');
        }

        const response = await fetch(`${this.baseUrl}${path}`, {
            ...options,
            headers
        });

        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get('Content-Type') || '';
        const isJson = contentType.includes('application/json');
        const payload = isJson ? await response.json() : await response.text();

        if (!response.ok) {
            const message = isJson && payload?.message
                    ? payload.message
                    : typeof payload === 'string' && payload.trim()
                            ? payload
                            : `Request failed with status ${response.status}`;
            const error = new Error(message);
            error.status = response.status;
            error.payload = payload;
            throw error;
        }

        return payload;
    },

    fetchWebhooks(page = 0, size = 20) {
        return this.request(`/webhooks?page=${page}&size=${size}`);
    },

    createWebhook(data) {
        return this.request('/webhooks', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    getWebhook(id) {
        return this.request(`/webhooks/${id}`);
    },

    updateWebhook(id, data) {
        return this.request(`/webhooks/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    deleteWebhook(id) {
        return this.request(`/webhooks/${id}`, {
            method: 'DELETE'
        });
    },

    toggleWebhook(id) {
        return this.request(`/webhooks/${id}/toggle`, {
            method: 'PATCH'
        });
    },

    fetchRequests(webhookId, page = 0, size = 20) {
        return this.request(`/webhooks/${webhookId}/requests?page=${page}&size=${size}`);
    },

    getRequest(webhookId, requestId) {
        return this.request(`/webhooks/${webhookId}/requests/${requestId}`);
    },

    clearRequests(webhookId) {
        return this.request(`/webhooks/${webhookId}/requests`, {
            method: 'DELETE'
        });
    },

    getStats(webhookId) {
        return this.request(`/webhooks/${webhookId}/stats`);
    },

    previewTemplate(template, data = {}) {
        return this.request('/templates/preview', {
            method: 'POST',
            body: JSON.stringify({ template, data })
        });
    }
};

export default API;
