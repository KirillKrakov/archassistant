import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ExtensionState } from '../../state/ExtensionState';
import {
  ActionItem,
  BackendInfoItem,
  CompileStatusItem,
  EmptyStateItem,
  ProjectInfoItem
} from './treeItems';
import { isProjectCompiled } from '../../utils/workspaceChecks';

export class ArchAssistantSidebarProvider implements vscode.TreeDataProvider<vscode.TreeItem> {
  private readonly emitter = new vscode.EventEmitter<vscode.TreeItem | undefined | void>();
  readonly onDidChangeTreeData = this.emitter.event;
  private backendConnected = false;

  constructor(
    private readonly backendClient: BackendClient,
    private readonly projectRegistry: ProjectRegistry,
    private readonly storageManager: ExtensionState
  ) {}

  setBackendConnected(connected: boolean): void {
    this.backendConnected = connected;
    this.refresh();
  }

  refresh(): void {
    this.emitter.fire();
  }

  getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: vscode.TreeItem): Promise<vscode.TreeItem[]> {
    if (element) return [];

    const project = this.projectRegistry.getCurrentProject();

    if (!project) {
      return [
        new ActionItem('Start Project', 'archassistant.startProject', 'Start Project', 'run'),
        new ActionItem('Configure Project', 'archassistant.configureProject', 'Configure Project', 'settings-gear'),
        new ActionItem('Generate Code', 'archassistant.generateCode', 'Generate Code', 'sparkle'),
        new ActionItem('Show Metrics', 'archassistant.showMetrics', 'Show Metrics', 'graph'),
        new BackendInfoItem(
          `Backend: ${this.storageManager.getBackendUrl()}`,
          `Backend URL: ${this.storageManager.getBackendUrl()}`,
          this.backendConnected
        ),
        new CompileStatusItem(false),
        new EmptyStateItem(
          'No project configured',
          'Click Start Project or Configure Project to begin'
        )
      ];
    }

    const compiled = await isProjectCompiled(project.projectPath);

    return [
      new ActionItem('Start Project', 'archassistant.startProject', 'Start Project', 'run'),
      new ActionItem('Configure Project', 'archassistant.configureProject', 'Configure Project', 'settings-gear'),
      new ActionItem('Generate Code', 'archassistant.generateCode', 'Generate Code', 'sparkle'),
      new ActionItem('Show Metrics', 'archassistant.showMetrics', 'Show Metrics', 'graph'),
      new BackendInfoItem(
        `Backend: ${this.storageManager.getBackendUrl()}`,
        `Backend URL: ${this.storageManager.getBackendUrl()}`,
        this.backendConnected
      ),
      new CompileStatusItem(compiled),
      new ProjectInfoItem(
        `Project: ${project.projectId}`,
        `${project.rulesCount ?? 0} rules`,
        `Project: ${project.projectId}\nPath: ${project.projectPath}\nRules: ${project.rulesCount ?? 0}`,
        {
          command: 'archassistant.configureProject',
          title: 'Configure Project'
        }
      )
    ];
  }
}