import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { ProjectRegistry } from '../state/projectRegistry';
import { projectIdFromPath } from '../utils/helpers';
import { toBackendProjectPath } from '../utils/projectPaths';
import { ArchAssistantSidebarProvider } from '../ui/sidebar/ArchAssistantSidebarProvider';

export async function configureProjectCommand(
  backendClient: BackendClient,
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  logger: Logger,
  sidebarProvider: ArchAssistantSidebarProvider,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  let current = registry.getCurrentProject();

  if (!current) {
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

    await registry.setCurrentProject(projectId, projectPath);
    await rulesManager.prepareProject(projectId, projectPath);
    current = { projectId, projectPath, rulesCount: 0 };
  } else {
    const hasLocalRulesConfig =
      state.getDraftRulesConfig() !== null || state.getSavedRulesConfig() !== null;

    if (!hasLocalRulesConfig) {
      await rulesManager.prepareProject(current.projectId, current.projectPath);
    }
  }

  const projectId = current.projectId;
  const projectPath = current.projectPath;
  const backendProjectPath = toBackendProjectPath(projectPath);

  await vscode.window.withProgress(
    {
      location: vscode.ProgressLocation.Notification,
      title: 'Configuring project...',
      cancellable: false
    },
    async (progress) => {
      progress.report({ message: 'Saving project path...' });
      try {
        await backendClient.saveProjectPath(projectId, backendProjectPath);
      } catch (error: any) {
        logger.warn('Could not save project path before scan: {}', error.message);
      }

      const localConfig = state.getSavedRulesConfig() ?? state.getDraftRulesConfig();
      if (localConfig) {
        try {
          await rulesManager.saveDraft(projectId);
        } catch (error: any) {
          logger.warn('Could not restore rules config before scan: {}', error.message);
        }
      }

      progress.report({ message: 'Scanning project structure...' });
      const suggestions = await rulesManager.refreshSuggestions(projectId, projectPath);
      await state.setSuggestions(suggestions);

      const draft = state.getDraftRulesConfig();
      await registry.updateRulesCount(projectId, draft?.rules.length ?? 0);

      progress.report({ message: 'Project prepared...' });
      logger.info(
        'Configured project {} with {} suggested rule modules',
        projectId,
        suggestions.length
      );
    }
  );

  sidebarProvider.refresh();
  rulesProvider.refresh();

  vscode.window.showInformationMessage(`Project configured successfully: ${projectId}`);
}