import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { MetricsPanel } from '../ui/webviews/metricsPanel';
import { logError } from '../utils/logger';

export async function showMetricsCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry
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

    MetricsPanel.createOrShow(backendClient, projectRegistry);
  } catch (error: any) {
    logError(`Show metrics command failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to open metrics panel: ${error.message}`);
  }
}