import * as vscode from 'vscode';
import { ArchitecturalRule, WorkspaceModuleSuggestions } from '../../backend/types';

export class RuleItem extends vscode.TreeItem {
  constructor(
    public readonly rule: ArchitecturalRule,
    public readonly source: 'saved' | 'suggested'
  ) {
    super(label(rule), vscode.TreeItemCollapsibleState.None);
    this.contextValue = source === 'saved' ? 'savedRule' : 'suggestedRule';
    this.tooltip = rule.description ? `${rule.name}\n${rule.description}` : rule.name;
    this.description = `${rule.type} · ${rule.constraint} · ${rule.enabled ? 'enabled' : 'disabled'}`;
    this.iconPath = new vscode.ThemeIcon(rule.enabled ? 'check' : 'circle-slash');
    this.command = source === 'saved'
      ? {
          command: 'archassistant.editRule',
          title: 'Edit Rule',
          arguments: [rule.id]
        }
      : {
          command: 'archassistant.addCustomRule',
          title: 'Add Suggested Rule',
          arguments: [rule]
        };
  }
}

export class RuleGroupItem extends vscode.TreeItem {
  constructor(title: string, count: number, public readonly kind: 'saved' | 'suggested') {
    super(`${title} (${count})`, vscode.TreeItemCollapsibleState.Expanded);
    this.contextValue = kind === 'saved' ? 'savedGroup' : 'suggestedGroup';
    this.iconPath = new vscode.ThemeIcon(kind === 'saved' ? 'library' : 'lightbulb');
  }
}

export class SuggestionModuleItem extends vscode.TreeItem {
  constructor(public readonly suggestion: WorkspaceModuleSuggestions) {
    super(suggestion.moduleRoot, vscode.TreeItemCollapsibleState.Expanded);
    this.description = suggestion.profile?.primaryProfile ? `${suggestion.profile.primaryProfile} (${suggestion.rules.length})` : `${suggestion.rules.length}`;
    this.contextValue = 'suggestionModule';
    this.iconPath = new vscode.ThemeIcon('package');
  }
}

function label(rule: ArchitecturalRule): string {
  const prefix = rule.enabled ? '☑' : '☐';
  return `${prefix} ${rule.name}`;
}
