import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ExtensionState } from '../../state/ExtensionState';
import {
  BackendInfoItem,
  CompileWarningItem,
  EmptyStateItem,
  ProjectInfoItem
} from './treeItems';

export class ArchAssistantSidebarProvider
  implements vscode.TreeDataProvider<vscode.TreeItem>
{
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
        new EmptyStateItem(
          'No project configured',
          'Click to configure a project',
          {
            command: 'archassistant.startProject',
            title: 'Start Project'
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

    return [
      new ProjectInfoItem(
        `Project: ${project.projectId}`,
        `${project.rulesCount ?? 0} rules`,
        `Project: ${project.projectId}\nPath: ${project.projectPath}\nRules: ${project.rulesCount ?? 0}`,
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