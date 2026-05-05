import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';

export async function saveRulesCommand(
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

  const config = state.getRulesConfig();
  await registry.updateRulesCount(current.projectId, config?.rules.length ?? 0);
  rulesProvider.refresh();

  logger.info('Saved rules for project {}', current.projectId);
  vscode.window.showInformationMessage(`Rules saved for ${current.projectId}`);
}