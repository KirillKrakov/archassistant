import * as vscode from 'vscode';
import {
  ArchitecturalRule,
  Severity,
  WorkspaceModuleSuggestions
} from '../../backend/types';

export class ActionItem extends vscode.TreeItem {
  constructor(
    label: string,
    command: string,
    title: string,
    icon: string,
    contextValue = 'action',
    args: any[] = []
  ) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.command = { command, title, arguments: args };
    this.iconPath = new vscode.ThemeIcon(icon);
    this.contextValue = contextValue;
  }
}

export class ProjectInfoItem extends vscode.TreeItem {
  constructor(label: string, description: string, tooltip: string, command?: vscode.Command) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.description = description;
    this.tooltip = tooltip;
    this.iconPath = new vscode.ThemeIcon('folder');
    if (command) this.command = command;
  }
}

export class BackendInfoItem extends vscode.TreeItem {
  constructor(label: string, tooltip: string, connected: boolean) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.tooltip = tooltip;
    this.iconPath = connected
      ? new vscode.ThemeIcon('check')
      : new vscode.ThemeIcon('error', new vscode.ThemeColor('problemsErrorIcon'));
  }
}

export class CompileStatusItem extends vscode.TreeItem {
  constructor(compiled: boolean) {
    super(
      compiled ? 'Project compiled for full validation' : 'Project must be compiled for full validation',
      vscode.TreeItemCollapsibleState.None
    );
    this.tooltip = compiled
      ? 'Compiled classes were found and full validation should work.'
      : 'Compile the project first (mvn compile / gradle build) for full validation.';
    this.iconPath = compiled
      ? new vscode.ThemeIcon('check', new vscode.ThemeColor('notificationsInfoIcon'))
      : new vscode.ThemeIcon('warning', new vscode.ThemeColor('notificationsWarningIcon'));
  }
}

export class EmptyStateItem extends vscode.TreeItem {
  constructor(label: string, tooltip: string, command?: vscode.Command) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.tooltip = tooltip;
    this.iconPath = new vscode.ThemeIcon('info');
    if (command) this.command = command;
  }
}

export class RuleGroupItem extends vscode.TreeItem {
  constructor(
    title: string,
    count: number,
    public readonly kind: 'saved' | 'suggested'
  ) {
    super(`${title} (${count})`, vscode.TreeItemCollapsibleState.Expanded);
    this.contextValue = kind === 'saved' ? 'savedGroup' : 'suggestedGroup';
    this.iconPath = new vscode.ThemeIcon(kind === 'saved' ? 'library' : 'lightbulb');
  }
}

export class SuggestionModuleItem extends vscode.TreeItem {
  constructor(public readonly suggestion: WorkspaceModuleSuggestions) {
    super(suggestion.moduleRoot, vscode.TreeItemCollapsibleState.Expanded);
    this.description = suggestion.profile?.primaryProfile
      ? `${suggestion.profile.primaryProfile} (${suggestion.rules.length})`
      : `${suggestion.rules.length}`;
    this.contextValue = 'suggestionModule';
    this.iconPath = new vscode.ThemeIcon('package');
  }
}

export class RuleItem extends vscode.TreeItem {
  constructor(
    public readonly rule: ArchitecturalRule,
    public readonly source: 'saved' | 'suggested'
  ) {
    super(label(rule), vscode.TreeItemCollapsibleState.None);
    this.description = `${rule.type} · ${rule.constraint} · ${rule.enabled ? 'enabled' : 'disabled'}`;
    this.tooltip = buildRuleTooltip(rule);
    this.iconPath = rule.enabled ? getSeverityIcon(rule.severity) : new vscode.ThemeIcon('circle-slash');
    this.contextValue = source === 'saved' ? 'savedRule' : 'suggestedRule';
  }
}

function label(rule: ArchitecturalRule): string {
  const prefix = rule.enabled ? '☑' : '☐';
  return `${prefix} ${rule.name}`;
}

function buildRuleTooltip(rule: ArchitecturalRule): string {
  return [
    `Rule: ${rule.name}`,
    `Type: ${rule.type}`,
    `Constraint: ${rule.constraint}`,
    `Severity: ${rule.severity}`,
    `Enabled: ${rule.enabled ? 'Yes' : 'No'}`,
    `From package: ${rule.from_package}`,
    rule.to_package ? `To package: ${rule.to_package}` : '',
    rule.to_packages?.length ? `To packages: ${rule.to_packages.join(', ')}` : '',
    rule.pattern ? `Pattern: ${rule.pattern}` : '',
    rule.annotation ? `Annotation: ${rule.annotation}` : '',
    rule.from_selector_mode ? `From selector: ${rule.from_selector_mode}` : '',
    rule.to_selector_mode ? `To selector: ${rule.to_selector_mode}` : '',
    rule.from_class_type ? `From class type: ${rule.from_class_type}` : '',
    rule.to_class_type ? `To class type: ${rule.to_class_type}` : '',
    rule.from_layer_type ? `From layer type: ${rule.from_layer_type}` : '',
    rule.to_layer_type ? `To layer type: ${rule.to_layer_type}` : '',
    rule.from_method_name_pattern ? `From method pattern: ${rule.from_method_name_pattern}` : '',
    rule.to_method_name_pattern ? `To method pattern: ${rule.to_method_name_pattern}` : '',
    rule.from_field_name_pattern ? `From field pattern: ${rule.from_field_name_pattern}` : '',
    rule.to_field_name_pattern ? `To field pattern: ${rule.to_field_name_pattern}` : '',
    rule.from_return_type ? `From return type: ${rule.from_return_type}` : '',
    rule.to_return_type ? `To return type: ${rule.to_return_type}` : '',
    rule.from_field_type ? `From field type: ${rule.from_field_type}` : '',
    rule.to_field_type ? `To field type: ${rule.to_field_type}` : '',
    rule.slice_pattern ? `Slice pattern: ${rule.slice_pattern}` : '',
    rule.max_cycle_length ? `Max cycle length: ${rule.max_cycle_length}` : ''
  ].filter(Boolean).join('\n');
}

function getSeverityIcon(severity: Severity): vscode.ThemeIcon {
  switch (severity) {
    case Severity.CRITICAL:
      return new vscode.ThemeIcon('error', new vscode.ThemeColor('notificationsErrorIcon'));
    case Severity.ERROR:
      return new vscode.ThemeIcon('error', new vscode.ThemeColor('problemsErrorIcon'));
    case Severity.WARNING:
      return new vscode.ThemeIcon('warning', new vscode.ThemeColor('problemsWarningIcon'));
    default:
      return new vscode.ThemeIcon('info', new vscode.ThemeColor('problemsInfoIcon'));
  }
}