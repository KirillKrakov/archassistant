import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';

export async function saveRulesCommand(
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  logger: Logger
): Promise<void> {
  if (!state.isBackendStarted()) {
    vscode.window.showWarningMessage('Backend is not started. Run ArchAssistant: Start Project first.');
    return;
  }

  const current = registry.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project selected. Use ArchAssistant: Start first.');
    return;
  }

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Saving ArchAssistant rules...',
      cancellable: false
    },
    async () => {
      await rulesManager.saveDraft(current.projectId);
    }
  );

  logger.info('Saved rules for project {}', current.projectId);
  vscode.window.showInformationMessage(`Rules saved for ${current.projectId}`);
}