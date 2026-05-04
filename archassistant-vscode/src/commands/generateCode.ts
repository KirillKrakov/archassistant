import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { StorageManager } from '../state/storage';
import { GenerateCodePanel } from '../ui/webviews/generatePanel';
import { logError } from '../utils/logger';

export async function generateCodeCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  storageManager: StorageManager
): Promise<void> {
  try {
    const project = await projectRegistry.getCurrentProject();
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
  } catch (error: any) {
    logError(`Generate code command failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to open generation panel: ${error.message}`);
  }
}