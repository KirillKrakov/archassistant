import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ArchitecturalRule } from '../../backend/types';
import { EmptyStateItem, RuleItem } from './treeItems';

export class RulesTreeDataProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<vscode.TreeItem | undefined | null | void>();
  readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

  private rules: ArchitecturalRule[] = [];

  constructor(
    private readonly backendClient: BackendClient,
    private readonly projectRegistry: ProjectRegistry
  ) {}

  async refresh(): Promise<void> {
    await this.loadRules();
    this.onDidChangeTreeDataEmitter.fire();
  }

  private async loadRules(): Promise<void> {
    const project = await this.projectRegistry.getCurrentProject();
    if (!project) {
      this.rules = [];
      return;
    }

    try {
      const config = await this.backendClient.getRules(project.projectId);
      this.rules = config.rules || [];
    } catch {
      this.rules = [];
    }
  }

  getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: vscode.TreeItem): Promise<vscode.TreeItem[]> {
    if (element) return [];

    const project = await this.projectRegistry.getCurrentProject();
    if (!project) {
      return [
        new EmptyStateItem(
          'No project configured',
          'Configure a project first',
          { command: 'archassistant.configureProject', title: 'Configure Project' }
        )
      ];
    }

    if (this.rules.length === 0) {
      await this.loadRules();
    }

    if (this.rules.length === 0) {
      return [
        new EmptyStateItem(
          'No rules configured',
          'Click to configure project rules',
          { command: 'archassistant.configureProject', title: 'Configure Project' }
        )
      ];
    }

    return this.rules.map(rule => new RuleItem(rule));
  }
}