import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { ProjectRegistry } from '../state/projectRegistry';
import { projectIdFromPath } from '../utils/helpers';
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
  const current = registry.getCurrentProject();

  let projectId = current?.projectId;
  let projectPath = current?.projectPath;

  if (!projectId || !projectPath) {
    const folder = await vscode.window.showOpenDialog({
      canSelectFolders: true,
      canSelectFiles: false,
      canSelectMany: false,
      openLabel: 'Select Project Root',
      title: 'Select Project Root Directory'
    });

    const selected = folder?.[0];
    if (!selected) return;

    projectPath = selected.fsPath;
    projectId = projectIdFromPath(projectPath);
  }

  const backendProjectPath = '/workspace/project';

  await state.resetProjectData();
  await registry.setCurrentProject(projectId, projectPath);
  await state.setRulesConfig(rulesManager.createEmptyConfig(projectId, projectPath));

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

      progress.report({ message: 'Scanning project structure...' });
      const suggestions = await rulesManager.refreshSuggestions(projectId, projectPath);
      await state.setSuggestions(suggestions);

      progress.report({ message: 'Project prepared...' });
      logger.info('Configured project {} with {} suggested rules', projectId, suggestions.reduce((sum, m) => sum + m.rules.length, 0));
    }
  );

  await registry.updateRulesCount(projectId, 0);
  sidebarProvider.refresh();
  rulesProvider.refresh();

  vscode.window.showInformationMessage(`Project configured successfully: ${projectId}`);
}