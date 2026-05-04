import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { ArchitecturalRule, RulesConfig } from '../backend/types';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { getRuleEditorHtml } from '../ui/webviews/rulesEditor';
import { logError, logInfo } from '../utils/logger';

export async function rescanRulesCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  try {
    const project = await projectRegistry.getCurrentProject();
    if (!project) {
      vscode.window.showWarningMessage('No project configured. Please configure a project first.');
      return;
    }

    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Re-scanning project...',
        cancellable: false
      },
      async progress => {
        progress.report({ message: 'Scanning project structure...' });
        const suggestions = await backendClient.suggestRules(project.projectId, project.projectPath);
        const mergedRules = suggestions.flatMap(s => s.rules || []);

        const config: RulesConfig = {
          version: '2.0',
          project_id: project.projectId,
          project_type: 'SPRING_BOOT',
          project_path: project.projectPath,
          rules: mergedRules.map(rule => ({ ...rule, enabled: rule.enabled ?? true, suggested: true }))
        };

        await backendClient.saveRules(config);
        await projectRegistry.updateRulesCount(project.projectId, mergedRules.length);
        await rulesProvider.refresh();

        vscode.window.showInformationMessage(`Re-scanned successfully. ${mergedRules.length} rule(s) updated.`);
      }
    );
  } catch (error: any) {
    logError(`Re-scan failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to re-scan project: ${error.message}`);
  }
}

export async function toggleRuleCommand(
  rule: ArchitecturalRule,
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  try {
    const project = await projectRegistry.getCurrentProject();
    if (!project) {
      vscode.window.showWarningMessage('No project configured.');
      return;
    }

    const config = await backendClient.getRules(project.projectId);
    const updatedRules = config.rules.map(r =>
      r.id === rule.id ? { ...r, enabled: !r.enabled } : r
    );

    await backendClient.saveRules({
      ...config,
      rules: updatedRules
    });

    await projectRegistry.updateRulesCount(project.projectId, updatedRules.length);
    await rulesProvider.refresh();
    logInfo(`Rule ${rule.id} toggled.`);
  } catch (error: any) {
    logError(`Toggle rule failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to toggle rule: ${error.message}`);
  }
}

export async function editRuleCommand(
  rule: ArchitecturalRule,
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  try {
    const project = await projectRegistry.getCurrentProject();
    if (!project) {
      vscode.window.showWarningMessage('No project configured.');
      return;
    }

    const panel = vscode.window.createWebviewPanel(
      'ruleEditor',
      `Edit Rule: ${rule.name}`,
      vscode.ViewColumn.One,
      { enableScripts: true }
    );

    panel.webview.html = getRuleEditorHtml(rule);

    panel.webview.onDidReceiveMessage(async message => {
      if (message?.command !== 'save') return;

      try {
        const config = await backendClient.getRules(project.projectId);
        const updatedRules = config.rules.map(r =>
          r.id === rule.id ? { ...r, ...message.rule } : r
        );

        await backendClient.saveRules({
          ...config,
          rules: updatedRules
        });

        await projectRegistry.updateRulesCount(project.projectId, updatedRules.length);
        await rulesProvider.refresh();

        vscode.window.showInformationMessage('Rule saved successfully.');
        panel.dispose();
      } catch (error: any) {
        vscode.window.showErrorMessage(`Failed to save rule: ${error.message}`);
      }
    });
  } catch (error: any) {
    logError(`Edit rule failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to edit rule: ${error.message}`);
  }
}