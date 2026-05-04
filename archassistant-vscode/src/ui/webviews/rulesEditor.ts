import { ArchitecturalRule, ConstraintType, Severity } from '../../backend/types';

export function getRuleEditorHtml(rule: ArchitecturalRule): string {
  const constraints = Object.values(ConstraintType)
    .map(c => `<option value="${c}" ${rule.constraint === c ? 'selected' : ''}>${c}</option>`)
    .join('\n');

  const severities = Object.values(Severity)
    .map(s => `<option value="${s}" ${rule.severity === s ? 'selected' : ''}>${s}</option>`)
    .join('\n');

  return `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 20px; }
    .row { margin-bottom: 12px; }
    label { display: block; margin-bottom: 6px; font-weight: 600; }
    input, select, textarea {
      width: 100%;
      box-sizing: border-box;
      padding: 8px;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border);
      border-radius: 4px;
    }
    textarea { min-height: 80px; }
    button {
      padding: 8px 14px;
      border: none;
      border-radius: 4px;
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
      cursor: pointer;
    }
    button:hover { background: var(--vscode-button-hoverBackground); }
  </style>
</head>
<body>
  <h2>Edit Rule</h2>

  <div class="row">
    <label>ID</label>
    <input id="id" type="text" value="${escapeHtml(rule.id)}" disabled />
  </div>

  <div class="row">
    <label>Name</label>
    <input id="name" type="text" value="${escapeHtml(rule.name)}" />
  </div>

  <div class="row">
    <label>Description</label>
    <textarea id="description">${escapeHtml(rule.description || '')}</textarea>
  </div>

  <div class="row">
    <label>Constraint</label>
    <select id="constraint">${constraints}</select>
  </div>

  <div class="row">
    <label>Severity</label>
    <select id="severity">${severities}</select>
  </div>

  <div class="row">
    <label>Enabled</label>
    <input id="enabled" type="checkbox" ${rule.enabled ? 'checked' : ''} />
  </div>

  <div class="row">
    <label>Pattern</label>
    <input id="pattern" type="text" value="${escapeHtml(rule.pattern || '')}" />
  </div>

  <div class="row">
    <label>Annotation</label>
    <input id="annotation" type="text" value="${escapeHtml(rule.annotation || '')}" />
  </div>

  <div class="row">
    <label>From Package</label>
    <input id="fromPackage" type="text" value="${escapeHtml(rule.from_package)}" />
  </div>

  <div class="row">
    <label>To Package</label>
    <input id="toPackage" type="text" value="${escapeHtml(rule.to_package || '')}" />
  </div>

  <button id="save">Save</button>

  <script>
    const vscode = acquireVsCodeApi();
    document.getElementById('save').addEventListener('click', () => {
      vscode.postMessage({
        command: 'save',
        rule: {
          name: document.getElementById('name').value,
          description: document.getElementById('description').value,
          constraint: document.getElementById('constraint').value,
          severity: document.getElementById('severity').value,
          enabled: document.getElementById('enabled').checked,
          pattern: document.getElementById('pattern').value,
          annotation: document.getElementById('annotation').value,
          from_package: document.getElementById('fromPackage').value,
          to_package: document.getElementById('toPackage').value
        }
      });
    });
  </script>
</body>
</html>
  `;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}