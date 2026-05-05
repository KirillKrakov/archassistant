import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { projectIdFromPath } from '../utils/helpers';

export async function configureProjectCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  logger: Logger,
  treeRefresh?: () => void
): Promise<void> {
  const current = state.getCurrentProject();
  let projectId = current?.projectId;
  let projectPath = current?.projectPath;

  if (!projectId || !projectPath) {
    const folder = await vscode.window.showOpenDialog({
      canSelectFolders: true,
      canSelectFiles: false,
      canSelectMany: false,
      openLabel: 'Select project folder'
    });

    const selected = folder?.[0];
    if (!selected) return;

    projectPath = selected.fsPath;
    projectId = projectIdFromPath(projectPath);
    await state.setCurrentProject(projectId, projectPath);
  }

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Loading ArchAssistant rules...',
      cancellable: false
    },
    async () => {
      const merged = await rulesManager.suggestAndMerge(projectId!, projectPath!);
      logger.info('Loaded {} rules for project {}', merged.rules.length, projectId);
    }
  );

  treeRefresh?.();
  vscode.window.showInformationMessage(`ArchAssistant project configured: ${projectId}`);
}