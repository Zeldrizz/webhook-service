/**
 * JSON viewer component
 * Formats and displays JSON with syntax highlighting and collapsible sections
 */
export function renderJsonViewer(container, data, depth = 0) {
    const el = document.createElement('div');
    el.className = 'json-viewer';
    el.innerHTML = renderValue(data, depth);
    container.appendChild(el);
}

function renderValue(value, depth) {
    if (value === null) return '<span class="json-null">null</span>';
    if (typeof value === 'boolean') return `<span class="json-bool">${value}</span>`;
    if (typeof value === 'number') return `<span class="json-number">${value}</span>`;
    if (typeof value === 'string') return `<span class="json-string">"${escHtml(value)}"</span>`;
    if (Array.isArray(value)) return renderArray(value, depth);
    if (typeof value === 'object') return renderObject(value, depth);
    return escHtml(String(value));
}

function renderObject(obj, depth) {
    const entries = Object.entries(obj);
    if (entries.length === 0) return '<span class="json-brace">{}</span>';

    const indent = '  '.repeat(depth + 1);
    const closingIndent = '  '.repeat(depth);

    let html = `<span class="json-toggle" onclick="this.parentElement.classList.toggle('collapsed')">{</span>`;
    html += `<div class="json-content">`;

    entries.forEach(([key, val], i) => {
        html += `${indent}<span class="json-key">"${escHtml(key)}"</span>: ${renderValue(val, depth + 1)}`;
        if (i < entries.length - 1) html += ',';
        html += '\n';
    });

    html += `</div>${closingIndent}<span class="json-brace">}</span>`;
    return `<pre class="json-block mb-0">${html}</pre>`;
}

function renderArray(arr, depth) {
    if (arr.length === 0) return '<span class="json-brace">[]</span>';

    const indent = '  '.repeat(depth + 1);
    const closingIndent = '  '.repeat(depth);

    let html = `<span class="json-toggle" onclick="this.parentElement.classList.toggle('collapsed')">[</span>`;
    html += `<div class="json-content">`;

    arr.forEach((val, i) => {
        html += `${indent}${renderValue(val, depth + 1)}`;
        if (i < arr.length - 1) html += ',';
        html += '\n';
    });

    html += `</div>${closingIndent}<span class="json-brace">]</span>`;
    return html;
}

function escHtml(s) {
    const d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
