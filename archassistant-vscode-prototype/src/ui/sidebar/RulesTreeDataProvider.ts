import * as vscode from 'vscode';
import { ExtensionState } from '../../state/ExtensionState';
import { RuleGroupItem, RuleItem, SuggestionModuleItem } from './treeItems';

export class RulesTreeDataProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly emitter = new vscode.EventEmitter<vscode.TreeItem | undefined | void>();
  readonly onDidChangeTreeData = this.emitter.event;

  constructor(private readonly state: ExtensionState) {}

  refresh(): void {
    this.emitter.fire();
  }

  getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(element?: vscode.TreeItem): vscode.ProviderResult<vscode.TreeItem[]> {
    const config = this.state.getRulesConfig();
    const suggestions = this.state.getSuggestions();

    if (!element) {
      return [
        new RuleGroupItem('Saved rules', config?.rules.length ?? 0, 'saved'),
        new RuleGroupItem('Suggested rules', suggestions.reduce((sum, module) => sum + module.rules.length, 0), 'suggested')
      ];
    }

    if (element instanceof RuleGroupItem && element.kind === 'saved') {
      return (config?.rules ?? []).map((rule) => new RuleItem(rule, 'saved'));
    }

    if (element instanceof RuleGroupItem && element.kind === 'suggested') {
      return suggestions.map((module) => new SuggestionModuleItem(module));
    }

    if (element instanceof SuggestionModuleItem) {
      return element.suggestion.rules.map((rule) => new RuleItem(rule, 'suggested'));
    }

    return [];
  }
}