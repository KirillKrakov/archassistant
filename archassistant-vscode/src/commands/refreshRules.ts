import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';

export async function refreshRulesCommand(
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  logger: Logger,
  treeRefresh?: () => void
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

  const merged = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Refreshing ArchAssistant suggestions...',
      cancellable: false
    },
    async () => rulesManager.suggestAndMerge(current.projectId, current.projectPath)
  );

  await rulesManager.saveDraft(current.projectId);
  await registry.updateRulesCount(current.projectId, merged.rules.length);

  logger.info('Refreshed project {} with {} rules', current.projectId, merged.rules.length);
  treeRefresh?.();
  vscode.window.showInformationMessage(`Rules refreshed for ${current.projectId}`);
}