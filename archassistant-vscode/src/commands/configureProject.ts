import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { ArchitecturalRule, RulesConfig, RuleSettings } from '../backend/types';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { ArchAssistantSidebarProvider } from '../ui/sidebar/ArchAssistantSidebarProvider';
import { getWorkspaceFolderName } from '../utils/helpers';
import { logError, logInfo } from '../utils/logger';

function createDefaultRulesConfig(projectId: string, projectPath: string): RulesConfig {
  return {
    version: '2.0',
    project_id: projectId,
    project_type: 'SPRING_BOOT',
    project_path: projectPath,
    rules: [],
    settings: {
      max_iterations: 3,
      timeout_seconds: 30,
      default_strategy: 'HYBRID',
      fail_on_critical: true,
      auto_fix_naming: false
    } satisfies RuleSettings
  };
}

function mergeSuggestedRules(rules: ArchitecturalRule[]): ArchitecturalRule[] {
  const seen = new Set<string>();
  const result: ArchitecturalRule[] = [];

  for (const rule of rules) {
    if (!rule?.id || seen.has(rule.id)) continue;
    seen.add(rule.id);
    result.push(rule);
  }

  return result;
}

export async function configureProjectCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  rulesProvider: RulesTreeDataProvider,
  sidebarProvider?: ArchAssistantSidebarProvider
): Promise<void> {
  try {
    const projectPathUris = await vscode.window.showOpenDialog({
      canSelectFolders: true,
      canSelectFiles: false,
      canSelectMany: false,
      openLabel: 'Select Project Root',
      title: 'Select Project Root Directory'
    });

    if (!projectPathUris?.length) return;

    const projectPath = projectPathUris[0].fsPath;
    const projectId = getWorkspaceFolderName() || projectPath.split(/[\\/]/).filter(Boolean).pop() || `project-${Date.now()}`;

    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Configuring project...',
        cancellable: false
      },
      async progress => {
        progress.report({ message: 'Saving project path...' });
        await backendClient.saveProjectPath(projectId, projectPath);

        progress.report({ message: 'Scanning project structure...' });
        const suggestions = await backendClient.suggestRules(projectId, projectPath);
        const mergedRules = mergeSuggestedRules(suggestions.flatMap(s => s.rules || []));

        progress.report({ message: 'Saving rules...' });
        const config = createDefaultRulesConfig(projectId, projectPath);
        config.rules = mergedRules.map(rule => ({ ...rule, enabled: rule.enabled ?? true, suggested: true }));

        await backendClient.saveRules(config);

        await projectRegistry.setCurrentProject(projectId, projectPath, mergedRules.length);
        await rulesProvider.refresh();
        sidebarProvider?.refresh();

        vscode.window.showInformationMessage(
          `Project configured successfully. ${mergedRules.length} rule(s) loaded.`
        );
      }
    );
  } catch (error: any) {
    logError(`Configure project failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to configure project: ${error.message}`);
  }
}