import {
  ArchitecturalRule,
  ClassType,
  ConstraintType,
  LayerType,
  RuleType,
  SelectorMode,
  Severity
} from '../../backend/types';

function enumOptions<T extends Record<string, string>>(
  values: T,
  selected: string | null | undefined
): string {
  return Object.values(values)
    .map((value) => `<option value="${escapeHtml(value)}" ${value === selected ? 'selected' : ''}>${escapeHtml(value)}</option>`)
    .join('\n');
}

function text(value: string | null | undefined): string {
  return escapeHtml(value ?? '');
}

export function getRuleEditorHtml(rule: ArchitecturalRule, title = 'Edit Rule'): string {
  const typeOptions = enumOptions(RuleType, rule.type);
  const constraintOptions = enumOptions(ConstraintType, rule.constraint);
  const severityOptions = enumOptions(Severity, rule.severity);
  const selectorOptions = enumOptions(SelectorMode, rule.from_selector_mode ?? SelectorMode.PACKAGE);
  const selectorOptionsTo = enumOptions(SelectorMode, rule.to_selector_mode ?? SelectorMode.PACKAGE);
  const classTypeOptions = enumOptions(ClassType, rule.from_class_type ?? null);
  const classTypeOptionsTo = enumOptions(ClassType, rule.to_class_type ?? null);
  const layerTypeOptions = enumOptions(LayerType, rule.from_layer_type ?? null);
  const layerTypeOptionsTo = enumOptions(LayerType, rule.to_layer_type ?? null);

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 20px; }
    h2 { margin: 0 0 16px 0; }
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
    textarea { min-height: 74px; resize: vertical; }
    .grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }
    .section {
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid var(--vscode-editorWidget-border);
    }
    .hidden { display: none; }
    .actions { display: flex; gap: 8px; margin-top: 16px; }
    button {
      padding: 8px 14px;
      border: none;
      border-radius: 4px;
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
      cursor: pointer;
    }
    button:hover { background: var(--vscode-button-hoverBackground); }
    button.secondary {
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
    }
    .error {
      margin: 12px 0;
      color: var(--vscode-problemsErrorIcon);
      min-height: 1.2em;
    }
    .hint { opacity: 0.8; font-size: 0.9rem; margin-top: 4px; }
  </style>
</head>
<body>
  <h2>${escapeHtml(title)}</h2>
  <div id="error" class="error"></div>

  <div class="row">
    <label>ID</label>
    <input id="id" type="text" value="${text(rule.id)}" disabled />
  </div>

  <div class="grid">
    <div class="row">
      <label>Name</label>
      <input id="name" type="text" value="${text(rule.name)}" />
    </div>

    <div class="row">
      <label>Type</label>
      <select id="type">${typeOptions}</select>
    </div>
  </div>

  <div class="row">
    <label>Description</label>
    <textarea id="description">${text(rule.description)}</textarea>
  </div>

  <div class="grid">
    <div class="row">
      <label>Constraint</label>
      <select id="constraint">${constraintOptions}</select>
    </div>

    <div class="row">
      <label>Severity</label>
      <select id="severity">${severityOptions}</select>
    </div>
  </div>

  <div class="grid">
    <div class="row">
      <label>Enabled</label>
      <input id="enabled" type="checkbox" ${rule.enabled ? 'checked' : ''} />
    </div>

    <div class="row">
      <label>From package</label>
      <input id="fromPackage" type="text" value="${text(rule.from_package)}" />
    </div>
  </div>

  <div class="section" id="dep-section">
    <h3>Dependency / Scope fields</h3>
    <div class="grid">
      <div class="row">
        <label>To package</label>
        <input id="toPackage" type="text" value="${text(rule.to_package)}" />
      </div>

      <div class="row">
        <label>To packages</label>
        <textarea id="toPackages">${text(rule.to_packages?.join('\n'))}</textarea>
        <div class="hint">One package per line</div>
      </div>

      <div class="row">
        <label>From selector mode</label>
        <select id="fromSelectorMode">
          ${enumOptions(SelectorMode, rule.from_selector_mode ?? SelectorMode.PACKAGE)}
        </select>
      </div>

      <div class="row">
        <label>To selector mode</label>
        <select id="toSelectorMode">
          ${enumOptions(SelectorMode, rule.to_selector_mode ?? SelectorMode.PACKAGE)}
        </select>
      </div>

      <div class="row">
        <label>From class type</label>
        <select id="fromClassType">
          <option value=""></option>
          ${classTypeOptions}
        </select>
      </div>

      <div class="row">
        <label>To class type</label>
        <select id="toClassType">
          <option value=""></option>
          ${classTypeOptionsTo}
        </select>
      </div>

      <div class="row">
        <label>From layer type</label>
        <select id="fromLayerType">
          <option value=""></option>
          ${layerTypeOptions}
        </select>
      </div>

      <div class="row">
        <label>To layer type</label>
        <select id="toLayerType">
          <option value=""></option>
          ${layerTypeOptionsTo}
        </select>
      </div>
    </div>
  </div>

  <div class="section" id="naming-section">
    <h3>Naming fields</h3>
    <div class="grid">
      <div class="row">
        <label>Pattern</label>
        <input id="pattern" type="text" value="${text(rule.pattern)}" />
      </div>
      <div class="row">
        <label>From name pattern</label>
        <input id="fromNamePattern" type="text" value="${text(rule.from_name_pattern)}" />
      </div>
      <div class="row">
        <label>To name pattern</label>
        <input id="toNamePattern" type="text" value="${text(rule.to_name_pattern)}" />
      </div>
      <div class="row">
        <label>From method name pattern</label>
        <input id="fromMethodNamePattern" type="text" value="${text(rule.from_method_name_pattern)}" />
      </div>
      <div class="row">
        <label>To method name pattern</label>
        <input id="toMethodNamePattern" type="text" value="${text(rule.to_method_name_pattern)}" />
      </div>
      <div class="row">
        <label>From field name pattern</label>
        <input id="fromFieldNamePattern" type="text" value="${text(rule.from_field_name_pattern)}" />
      </div>
      <div class="row">
        <label>To field name pattern</label>
        <input id="toFieldNamePattern" type="text" value="${text(rule.to_field_name_pattern)}" />
      </div>
    </div>
  </div>

  <div class="section" id="annotation-section">
    <h3>Annotation fields</h3>
    <div class="grid">
      <div class="row">
        <label>Annotation</label>
        <input id="annotation" type="text" value="${text(rule.annotation)}" />
      </div>
    </div>
  </div>

  <div class="section" id="signature-section">
    <h3>Method / Field / Exception fields</h3>
    <div class="grid">
      <div class="row">
        <label>From return type</label>
        <input id="fromReturnType" type="text" value="${text(rule.from_return_type)}" />
      </div>
      <div class="row">
        <label>To return type</label>
        <input id="toReturnType" type="text" value="${text(rule.to_return_type)}" />
      </div>

      <div class="row">
        <label>From parameter types</label>
        <textarea id="fromParameterTypes">${text(rule.from_parameter_types?.join('\n'))}</textarea>
        <div class="hint">One type per line</div>
      </div>
      <div class="row">
        <label>To parameter types</label>
        <textarea id="toParameterTypes">${text(rule.to_parameter_types?.join('\n'))}</textarea>
        <div class="hint">One type per line</div>
      </div>

      <div class="row">
        <label>From throws types</label>
        <textarea id="fromThrowsTypes">${text(rule.from_throws_types?.join('\n'))}</textarea>
        <div class="hint">One type per line</div>
      </div>
      <div class="row">
        <label>To throws types</label>
        <textarea id="toThrowsTypes">${text(rule.to_throws_types?.join('\n'))}</textarea>
        <div class="hint">One type per line</div>
      </div>

      <div class="row">
        <label>From modifiers</label>
        <textarea id="fromModifiers">${text(rule.from_modifiers?.join('\n'))}</textarea>
        <div class="hint">One modifier per line</div>
      </div>
      <div class="row">
        <label>To modifiers</label>
        <textarea id="toModifiers">${text(rule.to_modifiers?.join('\n'))}</textarea>
        <div class="hint">One modifier per line</div>
      </div>

      <div class="row">
        <label>From field type</label>
        <input id="fromFieldType" type="text" value="${text(rule.from_field_type)}" />
      </div>
      <div class="row">
        <label>To field type</label>
        <input id="toFieldType" type="text" value="${text(rule.to_field_type)}" />
      </div>

      <div class="row">
        <label>Slice pattern</label>
        <input id="slicePattern" type="text" value="${text(rule.slice_pattern)}" />
      </div>
      <div class="row">
        <label>Max cycle length</label>
        <input id="maxCycleLength" type="number" min="1" step="1" value="${rule.max_cycle_length ?? ''}" />
      </div>
    </div>
  </div>

  <div class="section" id="advanced-section">
    <h3>Advanced fields for CUSTOM rules</h3>
    <div class="hint">These fields stay visible only for CUSTOM rules.</div>
  </div>

  <div class="actions">
    <button id="save">Save</button>
    <button id="cancel" class="secondary">Cancel</button>
  </div>

  <script>
    const vscode = acquireVsCodeApi();

    function val(id) {
      return document.getElementById(id).value;
    }

    function checked(id) {
      return document.getElementById(id).checked;
    }

    function listFromText(id) {
      return val(id)
        .split(/\\r?\\n|,/g)
        .map((s) => s.trim())
        .filter(Boolean);
    }

    function show(id, visible) {
      document.getElementById(id).classList.toggle('hidden', !visible);
    }

    function updateSections() {
      const type = val('type');

      const isCustom = type === 'CUSTOM';
      const showDependency = [
        'DEPENDENCY',
        'INHERITANCE_CHECK',
        'INTERFACE_CHECK'
      ].includes(type);

      const showNaming = type === 'NAMING_CONVENTION';
      const showAnnotation = type === 'ANNOTATION_CHECK';
      const showSignature = [
        'METHOD_SIGNATURE_CHECK',
        'FIELD_CHECK',
        'EXCEPTION_CHECK',
        'MODIFIER_CHECK'
      ].includes(type);
      const showCycle = type === 'CYCLE_CHECK';

      show('dep-section', showDependency || isCustom);
      show('naming-section', showNaming || isCustom);
      show('annotation-section', showAnnotation || isCustom);
      show('signature-section', showSignature || showCycle || isCustom);
      show('advanced-section', isCustom);
    }

    document.getElementById('type').addEventListener('change', updateSections);

    document.getElementById('save').addEventListener('click', () => {
      const name = val('name').trim();
      const fromPackage = val('fromPackage').trim();

      if (!name) {
        document.getElementById('error').textContent = 'Name is required.';
        return;
      }

      if (!fromPackage) {
        document.getElementById('error').textContent = 'From package is required.';
        return;
      }

      const maxCycleRaw = val('maxCycleLength').trim();
      const maxCycleLength = maxCycleRaw ? Number(maxCycleRaw) : null;

      vscode.postMessage({
        command: 'save',
        rule: {
          name,
          description: val('description'),
          type: val('type'),
          from_package: fromPackage,
          to_package: val('toPackage'),
          to_packages: listFromText('toPackages'),
          constraint: val('constraint'),
          pattern: val('pattern'),
          annotation: val('annotation'),
          from_selector_mode: val('fromSelectorMode'),
          to_selector_mode: val('toSelectorMode'),
          from_class_type: val('fromClassType') || null,
          to_class_type: val('toClassType') || null,
          from_layer_type: val('fromLayerType') || null,
          to_layer_type: val('toLayerType') || null,
          from_name_pattern: val('fromNamePattern'),
          to_name_pattern: val('toNamePattern'),
          from_method_name_pattern: val('fromMethodNamePattern'),
          to_method_name_pattern: val('toMethodNamePattern'),
          from_field_name_pattern: val('fromFieldNamePattern'),
          to_field_name_pattern: val('toFieldNamePattern'),
          from_return_type: val('fromReturnType'),
          to_return_type: val('toReturnType'),
          from_parameter_types: listFromText('fromParameterTypes'),
          to_parameter_types: listFromText('toParameterTypes'),
          from_throws_types: listFromText('fromThrowsTypes'),
          to_throws_types: listFromText('toThrowsTypes'),
          from_modifiers: listFromText('fromModifiers'),
          to_modifiers: listFromText('toModifiers'),
          from_field_type: val('fromFieldType'),
          to_field_type: val('toFieldType'),
          slice_pattern: val('slicePattern'),
          max_cycle_length: Number.isFinite(maxCycleLength) ? maxCycleLength : null,
          severity: val('severity'),
          enabled: checked('enabled')
        }
      });
    });

    document.getElementById('cancel').addEventListener('click', () => {
      vscode.postMessage({ command: 'cancel' });
    });

    updateSections();
  </script>
</body>
</html>`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}