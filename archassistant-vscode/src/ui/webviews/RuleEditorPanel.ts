import * as vscode from 'vscode';
import { ArchitecturalRule, RuleType, SelectorMode } from '../../backend/types';
import { getRuleEditorHtml } from './rulesEditor';

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function toNonEmptyString(value: unknown, fallback: string): string {
  return isNonEmptyString(value) ? value.trim() : fallback;
}

function toNullableString(value: unknown): string | null {
  return isNonEmptyString(value) ? value.trim() : null;
}

function toNullableStringArray(value: unknown): string[] | null {
  if (!Array.isArray(value)) return null;
  const items = value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean);
  return items.length > 0 ? items : null;
}

function toNullableNumber(value: unknown, fallback: number | null = null): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function defaultConstraintForType(type: string): string {
  switch (type) {
    case RuleType.NAMING_CONVENTION:
      return 'NAMING_SUFFIX';
    case RuleType.ANNOTATION_CHECK:
      return 'HAS_ANNOTATION';
    case RuleType.DEPENDENCY:
      return 'NO_DEPENDENCY';
    case RuleType.LAYER_ISOLATION:
      return 'NO_DEPENDENCY';
    case RuleType.CYCLE_CHECK:
      return 'NO_CYCLE';
    case RuleType.INHERITANCE_CHECK:
      return 'SHOULD_EXTEND';
    case RuleType.INTERFACE_CHECK:
      return 'SHOULD_IMPLEMENT';
    case RuleType.MODIFIER_CHECK:
      return 'SHOULD_BE_PUBLIC';
    case RuleType.METHOD_SIGNATURE_CHECK:
      return 'RETURN_TYPE';
    case RuleType.FIELD_CHECK:
      return 'FIELD_TYPE';
    case RuleType.EXCEPTION_CHECK:
      return 'SHOULD_NOT_THROW';
    default:
      return 'CUSTOM';
  }
}

function sanitizeByType(rule: ArchitecturalRule): ArchitecturalRule {
  switch (rule.type) {
    case RuleType.NAMING_CONVENTION:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        from_layer_type: null,
        to_layer_type: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.ANNOTATION_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        pattern: null,
        from_layer_type: null,
        to_layer_type: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.DEPENDENCY:
      return {
        ...rule,
        annotation: null,
        pattern: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.LAYER_ISOLATION:
      return {
        ...rule,
        annotation: null,
        pattern: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.CYCLE_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_class_type: null,
        to_class_type: null,
        from_layer_type: null,
        to_layer_type: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null
      };

    case RuleType.INHERITANCE_CHECK:
    case RuleType.INTERFACE_CHECK:
      return {
        ...rule,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_layer_type: null,
        to_layer_type: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.MODIFIER_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_layer_type: null,
        to_layer_type: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.METHOD_SIGNATURE_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_class_type: null,
        to_class_type: null,
        from_layer_type: null,
        to_layer_type: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_field_type: null,
        to_field_type: null,
        from_throws_types: null,
        to_throws_types: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.FIELD_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_class_type: null,
        to_class_type: null,
        from_layer_type: null,
        to_layer_type: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_throws_types: null,
        to_throws_types: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    case RuleType.EXCEPTION_CHECK:
      return {
        ...rule,
        to_package: null,
        to_packages: null,
        annotation: null,
        pattern: null,
        from_class_type: null,
        to_class_type: null,
        from_layer_type: null,
        to_layer_type: null,
        from_name_pattern: null,
        to_name_pattern: null,
        from_method_name_pattern: null,
        to_method_name_pattern: null,
        from_field_name_pattern: null,
        to_field_name_pattern: null,
        from_return_type: null,
        to_return_type: null,
        from_parameter_types: null,
        to_parameter_types: null,
        from_modifiers: null,
        to_modifiers: null,
        from_field_type: null,
        to_field_type: null,
        slice_pattern: null,
        max_cycle_length: null
      };

    default:
      return rule;
  }
}

export class RuleEditorPanel {
  private static currentPanel: RuleEditorPanel | undefined;

  private readonly panel: vscode.WebviewPanel;
  private resolveResult: ((value: ArchitecturalRule | null) => void) | undefined;
  private disposed = false;

  private constructor(
    panel: vscode.WebviewPanel,
    private readonly initialRule: ArchitecturalRule,
    private readonly title: string,
    resolve: (value: ArchitecturalRule | null) => void
  ) {
    this.panel = panel;
    this.resolveResult = resolve;

    this.panel.onDidDispose(() => this.finish(null), null, []);
    this.panel.webview.onDidReceiveMessage((message) => this.handleMessage(message), null, []);
    this.render();
  }

  static open(initialRule: ArchitecturalRule, title = 'Edit Rule'): Promise<ArchitecturalRule | null> {
    if (RuleEditorPanel.currentPanel) {
      RuleEditorPanel.currentPanel.finish(null);
    }

    const panel = vscode.window.createWebviewPanel(
      'archassistant.ruleEditor',
      title,
      vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true
      }
    );

    return new Promise((resolve) => {
      RuleEditorPanel.currentPanel = new RuleEditorPanel(panel, initialRule, title, resolve);
    });
  }

  private render(): void {
    this.panel.title = this.title;
    this.panel.webview.html = getRuleEditorHtml(this.initialRule, this.title);
  }

  private async handleMessage(message: any): Promise<void> {
    switch (message?.command) {
      case 'save': {
        const payload = message.rule ?? {};
        const nextType = isNonEmptyString(payload.type) ? payload.type : this.initialRule.type;

        const merged: ArchitecturalRule = sanitizeByType({
          ...this.initialRule,
          id: this.initialRule.id,
          suggested: this.initialRule.suggested,
          name: toNonEmptyString(payload.name, this.initialRule.name),
          description: toNullableString(payload.description),
          type: nextType as ArchitecturalRule['type'],
          from_package: toNonEmptyString(payload.from_package, this.initialRule.from_package),
          to_package: toNullableString(payload.to_package),
          to_packages: toNullableStringArray(payload.to_packages),
          constraint: (isNonEmptyString(payload.constraint)
            ? payload.constraint
            : defaultConstraintForType(nextType)) as ArchitecturalRule['constraint'],
          pattern: toNullableString(payload.pattern),
          annotation: toNullableString(payload.annotation),
          from_selector_mode: (isNonEmptyString(payload.from_selector_mode)
            ? payload.from_selector_mode
            : SelectorMode.PACKAGE) as ArchitecturalRule['from_selector_mode'],
          to_selector_mode: (isNonEmptyString(payload.to_selector_mode)
            ? payload.to_selector_mode
            : SelectorMode.PACKAGE) as ArchitecturalRule['to_selector_mode'],
          from_class_type: toNullableString(payload.from_class_type) as ArchitecturalRule['from_class_type'],
          to_class_type: toNullableString(payload.to_class_type) as ArchitecturalRule['to_class_type'],
          from_layer_type: toNullableString(payload.from_layer_type) as ArchitecturalRule['from_layer_type'],
          to_layer_type: toNullableString(payload.to_layer_type) as ArchitecturalRule['to_layer_type'],
          from_name_pattern: toNullableString(payload.from_name_pattern),
          to_name_pattern: toNullableString(payload.to_name_pattern),
          from_method_name_pattern: toNullableString(payload.from_method_name_pattern),
          to_method_name_pattern: toNullableString(payload.to_method_name_pattern),
          from_field_name_pattern: toNullableString(payload.from_field_name_pattern),
          to_field_name_pattern: toNullableString(payload.to_field_name_pattern),
          from_return_type: toNullableString(payload.from_return_type),
          to_return_type: toNullableString(payload.to_return_type),
          from_parameter_types: toNullableStringArray(payload.from_parameter_types),
          to_parameter_types: toNullableStringArray(payload.to_parameter_types),
          from_throws_types: toNullableStringArray(payload.from_throws_types),
          to_throws_types: toNullableStringArray(payload.to_throws_types),
          from_modifiers: toNullableStringArray(payload.from_modifiers),
          to_modifiers: toNullableStringArray(payload.to_modifiers),
          from_field_type: toNullableString(payload.from_field_type),
          to_field_type: toNullableString(payload.to_field_type),
          slice_pattern: toNullableString(payload.slice_pattern),
          max_cycle_length: toNullableNumber(payload.max_cycle_length, this.initialRule.max_cycle_length ?? null),
          severity: (isNonEmptyString(payload.severity) ? payload.severity : this.initialRule.severity) as ArchitecturalRule['severity'],
          weight: toNullableNumber(payload.weight, this.initialRule.weight) ?? 1,
          enabled: typeof payload.enabled === 'boolean' ? payload.enabled : this.initialRule.enabled
        });

        this.finish(merged);
        break;
      }

      case 'cancel':
        this.finish(null);
        break;
    }
  }

  private finish(result: ArchitecturalRule | null): void {
    if (this.disposed) return;
    this.disposed = true;

    if (RuleEditorPanel.currentPanel === this) {
      RuleEditorPanel.currentPanel = undefined;
    }

    const resolve = this.resolveResult;
    this.resolveResult = undefined;

    try {
      resolve?.(result);
    } finally {
      this.panel.dispose();
    }
  }
}