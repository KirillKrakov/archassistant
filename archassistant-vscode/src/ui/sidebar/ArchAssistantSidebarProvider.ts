import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { StorageManager } from '../../state/storage';
import { BackendInfoItem, CompileWarningItem, EmptyStateItem, ProjectInfoItem } from './treeItems';

export class ArchAssistantSidebarProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<vscode.TreeItem | undefined | null | void>();
  readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

  private backendConnected = false;

  constructor(
    private readonly backendClient: BackendClient,
    private readonly projectRegistry: ProjectRegistry,
    private readonly storageManager: StorageManager
  ) {}

  setBackendConnected(connected: boolean): void {
    this.backendConnected = connected;
    this.refresh();
  }

  refresh(): void {
    this.onDidChangeTreeDataEmitter.fire();
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
          'Click to configure a project',
          { command: 'archassistant.configureProject', title: 'Configure Project' }
        ),
        new BackendInfoItem(
          `Backend: ${this.storageManager.getBackendUrl()}`,
          `Backend URL: ${this.storageManager.getBackendUrl()}`,
          this.backendConnected
        ),
        new CompileWarningItem()
      ];
    }

    return [
      new ProjectInfoItem(
        `Project: ${project.projectId}`,
        `${project.rulesCount} rules`,
        `Project: ${project.projectId}\nPath: ${project.projectPath}\nRules: ${project.rulesCount}`,
        {
          command: 'archassistant.configureProject',
          title: 'Reconfigure Project'
        }
      ),
      new BackendInfoItem(
        `Backend: ${this.storageManager.getBackendUrl()}`,
        `Backend URL: ${this.storageManager.getBackendUrl()}`,
        this.backendConnected
      ),
      new CompileWarningItem()
    ];
  }
}