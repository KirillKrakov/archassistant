import * as vscode from 'vscode';
import {
  ArchitecturalRule,
  ClassType,
  ConstraintType,
  RuleType,
  Severity,
  SelectorMode
} from '../backend/types';
import { normalizeProjectId } from '../utils/helpers';

export class RuleEditor {
  async createRule(
    projectId: string,
    defaults?: Partial<ArchitecturalRule>
  ): Promise<ArchitecturalRule | null> {
    const type = await this.pickRuleType(defaults?.type);
    if (!type) return null;

    const name = await this.promptText('Rule name', defaults?.name ?? 'Custom rule');
    if (name === undefined) return null;

    const description = await this.promptText('Description', defaults?.description ?? '');
    if (description === undefined) return null;

    const fromPackage = await this.promptText('From package pattern', defaults?.from_package ?? '');
    if (fromPackage === undefined) return null;

    const severity = await this.pickSeverity(defaults?.severity);
    if (!severity) return null;

    const enabled = await this.pickBoolean('Enable this rule?', defaults?.enabled ?? true);

    const rule = this.baseRule(
      projectId,
      name,
      description || undefined,
      type,
      fromPackage,
      severity,
      enabled
    );

    return this.populateByType(rule, defaults);
  }

  async editRule(rule: ArchitecturalRule): Promise<ArchitecturalRule | null> {
    const name = await this.promptText('Rule name', rule.name);
    if (name === undefined) return null;

    const description = await this.promptText('Description', rule.description ?? '');
    if (description === undefined) return null;

    const fromPackage = await this.promptText('From package pattern', rule.from_package);
    if (fromPackage === undefined) return null;

    const severity = await this.pickSeverity(rule.severity);
    if (!severity) return null;

    const enabled = await this.pickBoolean('Enable this rule?', rule.enabled);

    return {
      ...rule,
      name,
      description: description || null,
      from_package: fromPackage,
      severity,
      enabled
    };
  }

  private baseRule(
    projectId: string,
    name: string,
    description: string | undefined,
    type: RuleType,
    fromPackage: string,
    severity: Severity,
    enabled: boolean
  ): ArchitecturalRule {
    const id = `${normalizeProjectId(projectId)}_${normalizeProjectId(name)}_${Date.now()}`;
    return {
      id,
      name,
      description: description ?? null,
      type,
      from_package: fromPackage,
      constraint: this.defaultConstraint(type),
      severity,
      weight: 1.0,
      enabled,
      suggested: false
    };
  }

  private populateByType(
    rule: ArchitecturalRule,
    defaults?: Partial<ArchitecturalRule>
  ): ArchitecturalRule {
    switch (rule.type) {
      case RuleType.NAMING_CONVENTION:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.NAMING_SUFFIX,
          pattern: defaults?.pattern ?? 'Service',
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.PACKAGE
        };

      case RuleType.ANNOTATION_CHECK:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.HAS_ANNOTATION,
          annotation: defaults?.annotation ?? '@Service',
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.PACKAGE
        };

      case RuleType.DEPENDENCY:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.NO_DEPENDENCY,
          to_package: defaults?.to_package ?? rule.from_package,
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.PACKAGE,
          to_selector_mode: defaults?.to_selector_mode ?? SelectorMode.PACKAGE,
          from_class_type: defaults?.from_class_type ?? null,
          to_class_type: defaults?.to_class_type ?? null
        };

      case RuleType.MODIFIER_CHECK:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.SHOULD_BE_PUBLIC,
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.CLASS_TYPE,
          from_class_type: defaults?.from_class_type ?? ClassType.SERVICE
        };

      case RuleType.EXCEPTION_CHECK:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.SHOULD_NOT_THROW,
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.CLASS_TYPE,
          from_class_type: defaults?.from_class_type ?? ClassType.SERVICE,
          from_throws_types: defaults?.from_throws_types ?? ['java.lang.Exception']
        };

      case RuleType.INHERITANCE_CHECK:
      case RuleType.INTERFACE_CHECK:
        return {
          ...rule,
          constraint:
            defaults?.constraint ??
            (rule.type === RuleType.INHERITANCE_CHECK
              ? ConstraintType.SHOULD_EXTEND
              : ConstraintType.SHOULD_IMPLEMENT),
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.CLASS_TYPE,
          from_class_type: defaults?.from_class_type ?? ClassType.SERVICE,
          to_package: defaults?.to_package ?? 'java.lang.Object'
        };

      case RuleType.FIELD_CHECK:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.FIELD_TYPE,
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.CLASS_TYPE,
          from_class_type: defaults?.from_class_type ?? ClassType.SERVICE,
          from_field_type: defaults?.from_field_type ?? 'java.lang.String'
        };

      case RuleType.METHOD_SIGNATURE_CHECK:
        return {
          ...rule,
          constraint: defaults?.constraint ?? ConstraintType.RETURN_TYPE,
          from_selector_mode: defaults?.from_selector_mode ?? SelectorMode.CLASS_TYPE,
          from_class_type: defaults?.from_class_type ?? ClassType.CONTROLLER,
          from_method_name_pattern: defaults?.from_method_name_pattern ?? '*',
          from_return_type: defaults?.from_return_type ?? 'java.lang.String'
        };

      default:
        return rule;
    }
  }

  private defaultConstraint(type: RuleType): ConstraintType {
    switch (type) {
      case RuleType.NAMING_CONVENTION:
        return ConstraintType.NAMING_SUFFIX;
      case RuleType.ANNOTATION_CHECK:
        return ConstraintType.HAS_ANNOTATION;
      case RuleType.DEPENDENCY:
        return ConstraintType.NO_DEPENDENCY;
      case RuleType.CYCLE_CHECK:
        return ConstraintType.NO_CYCLE;
      case RuleType.INHERITANCE_CHECK:
        return ConstraintType.SHOULD_EXTEND;
      case RuleType.INTERFACE_CHECK:
        return ConstraintType.SHOULD_IMPLEMENT;
      case RuleType.MODIFIER_CHECK:
        return ConstraintType.SHOULD_BE_PUBLIC;
      case RuleType.METHOD_SIGNATURE_CHECK:
        return ConstraintType.RETURN_TYPE;
      case RuleType.FIELD_CHECK:
        return ConstraintType.FIELD_TYPE;
      case RuleType.EXCEPTION_CHECK:
        return ConstraintType.SHOULD_NOT_THROW;
      default:
        return ConstraintType.CUSTOM;
    }
  }

  private async promptText(
    placeHolder: string,
    value: string
  ): Promise<string | undefined> {
    return vscode.window.showInputBox({
      prompt: placeHolder,
      value,
      ignoreFocusOut: true
    });
  }

  private async pickSeverity(current?: Severity): Promise<Severity | undefined> {
    const picked = await vscode.window.showQuickPick(
      [Severity.INFO, Severity.WARNING, Severity.ERROR, Severity.CRITICAL].map((item) => ({
        label: item,
        value: item
      })),
      {
        placeHolder: 'Select severity',
        ignoreFocusOut: true
      }
    );
    return picked?.value ?? current;
  }

  private async pickBoolean(placeHolder: string, current: boolean): Promise<boolean> {
    const picked = await vscode.window.showQuickPick(
      [
        { label: 'Yes', value: true },
        { label: 'No', value: false }
      ],
      {
        placeHolder,
        ignoreFocusOut: true
      }
    );
    return picked?.value ?? current;
  }

  private async pickRuleType(current?: RuleType): Promise<RuleType | undefined> {
    const items: RuleType[] = [
      RuleType.DEPENDENCY,
      RuleType.NAMING_CONVENTION,
      RuleType.ANNOTATION_CHECK,
      RuleType.LAYER_ISOLATION,
      RuleType.CYCLE_CHECK,
      RuleType.INHERITANCE_CHECK,
      RuleType.INTERFACE_CHECK,
      RuleType.MODIFIER_CHECK,
      RuleType.METHOD_SIGNATURE_CHECK,
      RuleType.FIELD_CHECK,
      RuleType.EXCEPTION_CHECK,
      RuleType.CUSTOM
    ];

    const selected = await vscode.window.showQuickPick(
      items.map((item) => ({ label: item, value: item })),
      {
        placeHolder: 'Select rule type',
        ignoreFocusOut: true
      }
    );

    return selected?.value ?? current;
  }
}