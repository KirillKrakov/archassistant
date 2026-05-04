import * as vscode from 'vscode';
import { ArchitecturalRule, Severity } from '../../backend/types';

export class ProjectInfoItem extends vscode.TreeItem {
  constructor(
    label: string,
    description: string,
    tooltip: string,
    command?: vscode.Command
  ) {
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

export class CompileWarningItem extends vscode.TreeItem {
  constructor() {
    super('Project must be compiled for full validation', vscode.TreeItemCollapsibleState.None);
    this.tooltip = 'For full ArchUnit validation, compile your project first (mvn compile / gradle build).';
    this.iconPath = new vscode.ThemeIcon('warning', new vscode.ThemeColor('notificationsWarningIcon'));
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

export class RuleItem extends vscode.TreeItem {
  constructor(rule: ArchitecturalRule) {
    super(rule.name, vscode.TreeItemCollapsibleState.None);
    this.rule = rule;
    this.description = `[${rule.severity}]`;
    this.tooltip = buildRuleTooltip(rule);
    this.iconPath = rule.enabled ? getSeverityIcon(rule.severity) : new vscode.ThemeIcon('circle-slash');
    this.contextValue = 'archassistant.rule';
    this.command = {
      command: 'archassistant.toggleRule',
      title: 'Toggle Rule',
      arguments: [rule]
    };
  }

  rule: ArchitecturalRule;
}

function buildRuleTooltip(rule: ArchitecturalRule): string {
  return [
    `Rule: ${rule.name}`,
    `Type: ${rule.type}`,
    `Constraint: ${rule.constraint}`,
    `Severity: ${rule.severity}`,
    `Enabled: ${rule.enabled ? 'Yes' : 'No'}`,
    `From: ${rule.from_package}`,
    rule.to_package ? `To: ${rule.to_package}` : '',
    rule.pattern ? `Pattern: ${rule.pattern}` : '',
    rule.annotation ? `Annotation: ${rule.annotation}` : ''
  ]
    .filter(Boolean)
    .join('\n');
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