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
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  const current = registry.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  const merged = await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Refreshing ArchAssistant suggestions...',
      cancellable: false
    },
    async () => rulesManager.refreshSuggestions(current.projectId, current.projectPath)
  );

  await state.setSuggestions(merged);
  rulesProvider.refresh();

  logger.info('Refreshed project {} with {} suggested rule modules', current.projectId, merged.length);
  vscode.window.showInformationMessage(`Suggestions refreshed for ${current.projectId}`);
}