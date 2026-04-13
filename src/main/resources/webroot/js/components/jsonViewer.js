/**
 * JSON viewer component.
 * Formats and displays JSON with lightweight syntax highlighting.
 */

export function renderJsonViewer(container, data) {
    const pre = document.createElement('pre');
    pre.className = 'json-block mb-0';
    pre.innerHTML = highlightJson(JSON.stringify(data, null, 2));
    container.innerHTML = '';
    container.appendChild(pre);
}

function highlightJson(jsonString) {
    const escaped = escapeHtml(jsonString);
    return escaped.replace(
        /(\"(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\\"])*\"\s*:?)|(\btrue\b|\bfalse\b)|(\bnull\b)|(-?\d+(?:\.\d+)?(?:[eE][+\-]?\d+)?)/g,
        (match, stringLiteral, boolLiteral, nullLiteral, numberLiteral) => {
            if (stringLiteral) {
                return stringLiteral.endsWith(':')
                        ? `<span class="json-key">${stringLiteral}</span>`
                        : `<span class="json-string">${stringLiteral}</span>`;
            }
            if (boolLiteral) return `<span class="json-bool">${boolLiteral}</span>`;
            if (nullLiteral) return `<span class="json-null">${nullLiteral}</span>`;
            if (numberLiteral) return `<span class="json-number">${numberLiteral}</span>`;
            return match;
        }
    );
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}
