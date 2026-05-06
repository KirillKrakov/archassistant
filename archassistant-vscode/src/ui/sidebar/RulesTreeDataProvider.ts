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
import { ruleKey } from '../../utils/helpers';
import { ArchitecturalRule } from '../../backend/types';

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
    const draft = this.state.getDraftRulesConfig();
    const suggestions = this.state.getSuggestions();

    if (!element) {
      if (!project) {
        return [
          new EmptyStateItem(
            'No project configured',
            'Configure a project first',
            { command: 'archassistant.startProject', title: 'Start Project' }
          )
        ];
      }

      return [
        new RuleGroupItem('Saved rules', draft?.rules.length ?? 0, 'saved'),
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

      const rules = draft?.rules ?? [];
      if (rules.length === 0) {
        items.push(new EmptyStateItem('Saved rules are empty', 'Use Get Actual Rules or Add Custom Rule'));
      } else {
        items.push(...rules.map((rule) => new RuleItem(rule, 'saved')));
      }

      return items;
    }

    if (element instanceof RuleGroupItem && element.kind === 'suggested') {
      const filteredSuggestions = this.getUnaddedSuggestions();

      const items: vscode.TreeItem[] = [
        new ActionItem('Add Custom Rule', 'archassistant.addCustomRule', 'Add Custom Rule', 'plus'),
        new ActionItem('Add Suggested Rule', 'archassistant.addSuggestedRule', 'Add Suggested Rule', 'plus'),
        new ActionItem('Refresh Suggestions', 'archassistant.refreshRules', 'Refresh Suggestions', 'refresh')
      ];

      if (filteredSuggestions.length === 0) {
        items.push(new EmptyStateItem('No suggested rules yet', 'Click Refresh Suggestions'));
        return items;
      }

      for (const module of filteredSuggestions) {
        items.push(new SuggestionModuleItem(module));
      }

      return items;
    }

    if (element instanceof SuggestionModuleItem) {
      return element.suggestion.rules
        .filter((rule) => !this.isAlreadySaved(rule))
        .map((rule) => new RuleItem(rule, 'suggested'));
    }

    return [];
  }

  private getUnaddedSuggestions() {
    return this.state.getSuggestions()
      .map((module) => ({
        ...module,
        rules: module.rules.filter((rule) => !this.isAlreadySaved(rule))
      }))
      .filter((module) => module.rules.length > 0);
  }

  private isAlreadySaved(rule: ArchitecturalRule): boolean {
    const draft = this.state.getDraftRulesConfig();
    const savedRules = draft?.rules ?? [];
    const target = this.normalize(rule);
    return savedRules.some((saved) => this.normalize(saved) === target);
  }

  private normalize(rule: ArchitecturalRule): string {
    return ruleKey(rule);
  }
}