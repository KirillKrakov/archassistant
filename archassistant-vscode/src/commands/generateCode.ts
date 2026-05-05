import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { ExtensionState } from '../state/ExtensionState';
import { GenerateCodePanel } from '../ui/webviews/generatePanel';

export async function generateCodeCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  storageManager: ExtensionState
): Promise<void> {
  const project = projectRegistry.getCurrentProject();
  if (!project) {
    const action = await vscode.window.showWarningMessage(
      'No project configured. Configure a project first?',
      'Configure',
      'Cancel'
    );

    if (action === 'Configure') {
      await vscode.commands.executeCommand('archassistant.configureProject');
    }
    return;
  }

  GenerateCodePanel.createOrShow(backendClient, projectRegistry, storageManager);
}