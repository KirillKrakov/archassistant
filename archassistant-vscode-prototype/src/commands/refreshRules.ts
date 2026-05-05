import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';

export async function refreshRulesCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  logger: Logger,
  treeRefresh?: () => void
): Promise<void> {
  const current = state.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project selected. Use ArchAssistant: Start first.');
    return;
  }

  const merged = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Refreshing ArchAssistant suggestions...',
      cancellable: false
    },
    async () => rulesManager.suggestAndMerge(current.projectId, current.projectPath)
  );

  logger.info('Refreshed project {} with {} rules', current.projectId, merged.rules.length);
  treeRefresh?.();
  vscode.window.showInformationMessage(`Rules refreshed for ${current.projectId}`);
}