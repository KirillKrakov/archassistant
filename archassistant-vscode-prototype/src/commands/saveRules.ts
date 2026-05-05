import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';

export async function saveRulesCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  logger: Logger
): Promise<void> {
  const current = state.getCurrentProject();
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
