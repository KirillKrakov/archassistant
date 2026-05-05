import * as vscode from 'vscode';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { ProjectRegistry } from '../state/projectRegistry';
import { projectIdFromPath } from '../utils/helpers';

export async function configureProjectCommand(
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  logger: Logger,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  if (!state.isBackendStarted()) {
    vscode.window.showWarningMessage(
      'Backend is not started. Run ArchAssistant: Start Project first so the Docker mount is available.'
    );
    return;
  }

  const folder = await vscode.window.showOpenDialog({
    canSelectFolders: true,
    canSelectFiles: false,
    canSelectMany: false,
    openLabel: 'Select Project Root',
    title: 'Select Project Root Directory'
  });

  const selected = folder?.[0];
  if (!selected) return;

  const projectPath = selected.fsPath;
  const projectId = projectIdFromPath(projectPath);

  await state.resetProjectData();
  await registry.setCurrentProject(projectId, projectPath);

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Configuring project...',
      cancellable: false
    },
    async (progress) => {
      progress.report({ message: 'Scanning project structure...' });
      const merged = await rulesManager.suggestAndMerge(projectId, projectPath);

      progress.report({ message: 'Saving rules...' });
      await rulesManager.saveDraft(projectId);

      await registry.updateRulesCount(projectId, merged.rules.length);
      logger.info('Configured project {} with {} rules', projectId, merged.rules.length);
    }
  );

  rulesProvider.refresh();
  vscode.window.showInformationMessage(`Project configured successfully: ${projectId}`);
}