import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { DockerBackendLauncher } from '../backend/DockerBackendLauncher';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { Logger } from '../utils/logger';
import { projectIdFromPath } from '../utils/helpers';
import { toBackendProjectPath } from '../utils/projectPaths';
import { ArchAssistantSidebarProvider } from '../ui/sidebar/ArchAssistantSidebarProvider';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';

export async function startProjectCommand(
  backendClient: BackendClient,
  state: ExtensionState,
  registry: ProjectRegistry,
  sidebarProvider: ArchAssistantSidebarProvider,
  rulesProvider: RulesTreeDataProvider,
  logger: Logger
): Promise<void> {
  const folder = await vscode.window.showOpenDialog({
    canSelectFolders: true,
    canSelectFiles: false,
    canSelectMany: false,
    openLabel: 'Select project folder'
  });

  const selected = folder?.[0];
  if (!selected) return;

  const projectPath = selected.fsPath;
  const projectId = projectIdFromPath(projectPath);
  const cfg = vscode.workspace.getConfiguration('archassistant');
  const backendUrl = cfg.get<string>('backendUrl', 'http://localhost:8080');
  let composeDirectory = cfg.get<string>('composeDirectory', '');
  const serviceName = cfg.get<string>('composeServiceName', 'backend');
  const autoStart = cfg.get<boolean>('autoStartBackend', true);

  await state.resetSessionState();
  await registry.setCurrentProject(projectId, projectPath);

  type LlmProvider = 'mistral' | 'gigachat';

  const providerPick = await vscode.window.showQuickPick(
    [
      { label: 'Mistral', description: 'Default provider', value: 'mistral' as LlmProvider },
      { label: 'GigaChat', description: 'Sber provider', value: 'gigachat' as LlmProvider }
    ],
    {
      title: 'Select LLM provider',
      placeHolder: 'Choose the provider for the backend'
    }
  );

  if (!providerPick) {
    return;
  }

  const llmProvider = providerPick.value  as 'mistral' | 'gigachat';

  if (autoStart) {
    if (!composeDirectory) {
      const composeFolder = await vscode.window.showOpenDialog({
        canSelectFolders: true,
        canSelectFiles: false,
        canSelectMany: false,
        openLabel: 'Select folder with docker-compose.yml'
      });

      const selectedCompose = composeFolder?.[0]?.fsPath;
      if (!selectedCompose) {
        throw new Error('Compose directory is required to start the backend');
      }

      composeDirectory = selectedCompose;
      await cfg.update('composeDirectory', composeDirectory, vscode.ConfigurationTarget.Global);
    }

    const launcher = new DockerBackendLauncher();
    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Starting ArchAssistant backend...',
        cancellable: false
      },
      async () => {
        await launcher.start({
          projectPath,
          composeDirectory,
          serviceName,
          backendUrl,
          llmProvider
        });
      }
    );

    await state.setBackendStarted(true);
    await state.setBackendLaunchInfo({
      projectPath,
      composeDirectory,
      serviceName,
      backendUrl,
      llmProvider
    });
  }

  const backendProjectPath = toBackendProjectPath(projectPath);

  try {
    await backendClient.saveProjectPath(projectId, backendProjectPath);
  } catch (error: any) {
    logger.warn('Could not save project path to backend: {}', error.message);
  }

  sidebarProvider.refresh();
  rulesProvider.refresh();

  vscode.window.showInformationMessage(`ArchAssistant started for project ${projectId}`);
  logger.info('Started project {} at {} using provider {}', projectId, backendProjectPath, llmProvider);
}