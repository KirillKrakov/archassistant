import * as vscode from 'vscode';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ExtensionState } from '../../state/ExtensionState';
import {
  ActionItem,
  EmptyStateItem,
  RuleGroupItem,
  RuleItem,
  SuggestionModuleItem
} from './treeItems';

export class RulesTreeDataProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly emitter = new vscode.EventEmitter<vscode.TreeItem | undefined | void>();
  readonly onDidChangeTreeData = this.emitter.event;

  constructor(
    private readonly projectRegistry: ProjectRegistry,
    private readonly state: ExtensionState
  ) {}

  refresh(): void {
    this.emitter.fire();
  }

  getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: vscode.TreeItem): Promise<vscode.TreeItem[]> {
    const project = this.projectRegistry.getCurrentProject();
    const config = this.state.getRulesConfig();
    const suggestions = this.state.getSuggestions();

    if (!element) {
      if (!project) {
        return [
          new EmptyStateItem(
            'No project configured',
            'Configure a project first',
            {
              command: 'archassistant.startProject',
              title: 'Start Project'
            }
          )
        ];
      }

      return [
        new RuleGroupItem('Saved rules', config?.rules.length ?? 0, 'saved'),
        new RuleGroupItem(
          'Suggested rules',
          suggestions.reduce((sum, module) => sum + module.rules.length, 0),
          'suggested'
        )
      ];
    }

    if (element instanceof RuleGroupItem && element.kind === 'saved') {
      const items: vscode.TreeItem[] = [
        new ActionItem('Get Actual Rules', 'archassistant.getActualRules', 'Get Actual Rules', 'cloud-download'),
        new ActionItem('Save Rules', 'archassistant.saveRules', 'Save Rules', 'save-all'),
        new ActionItem('Edit Rule', 'archassistant.editRule', 'Edit Rule', 'edit'),
        new ActionItem('Delete Rule', 'archassistant.deleteRule', 'Delete Rule', 'trash')
      ];

      const rules = config?.rules ?? [];
      if (rules.length === 0) {
        items.push(new EmptyStateItem('Saved rules are empty', 'Use Get Actual Rules or Add Custom Rule'));
      } else {
        items.push(...rules.map((rule) => new RuleItem(rule, 'saved')));
      }

      return items;
    }

    if (element instanceof RuleGroupItem && element.kind === 'suggested') {
      const items: vscode.TreeItem[] = [
        new ActionItem('Add Custom Rule', 'archassistant.addCustomRule', 'Add Custom Rule', 'plus'),
        new ActionItem('Refresh Suggestions', 'archassistant.refreshRules', 'Refresh Suggestions', 'refresh')
      ];

      if (suggestions.length === 0) {
        items.push(new EmptyStateItem('No suggested rules yet', 'Click Refresh Suggestions'));
        return items;
      }

      for (const module of suggestions) {
        items.push(new SuggestionModuleItem(module));
      }

      return items;
    }

    if (element instanceof SuggestionModuleItem) {
      return element.suggestion.rules.map((rule) => new RuleItem(rule, 'suggested'));
    }

    return [];
  }
}