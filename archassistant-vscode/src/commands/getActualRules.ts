import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { RulesManager } from '../services/RulesManager';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';

export async function getActualRulesCommand(
  backendClient: BackendClient,
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  rulesProvider: RulesTreeDataProvider
): Promise<void> {
  const current = registry.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project configured.');
    return;
  }

  const actual = await rulesManager.loadActualRules(current.projectId);
  await registry.updateRulesCount(current.projectId, actual.rules.length);
  rulesProvider.refresh();

  vscode.window.showInformationMessage(
    `Loaded ${actual.rules.length} saved rule(s) from backend for ${current.projectId}`
  );
}