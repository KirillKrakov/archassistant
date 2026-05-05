import * as vscode from 'vscode';
import { ArchitecturalRule } from '../backend/types';
import { ProjectRegistry } from '../state/projectRegistry';
import { RulesManager } from '../services/RulesManager';
import { RuleEditor } from '../services/RuleEditor';
import { ExtensionState } from '../state/ExtensionState';

export async function toggleRuleCommand(
  ruleId: string,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  const updated = await rulesManager.toggleRule(ruleId);
  await rulesManager.saveDraft(updated.project_id);
  refresh();
}

export async function editRuleCommand(
  ruleId: string,
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void
): Promise<void> {
  const config = state.getRulesConfig();
  const rule = config?.rules.find((item) => item.id === ruleId);
  if (!rule) {
    vscode.window.showWarningMessage(`Rule not found: ${ruleId}`);
    return;
  }

  const updatedRule = await editor.editRule(rule);
  if (!updatedRule) return;

  const updated = await rulesManager.updateRule(ruleId, () => updatedRule);
  await rulesManager.saveDraft(updated.project_id);
  refresh();
}

export async function deleteRuleCommand(
  ruleId: string,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  const confirmed = await vscode.window.showWarningMessage(
    `Delete rule ${ruleId}?`,
    { modal: true },
    'Delete'
  );

  if (confirmed !== 'Delete') return;

  const updated = await rulesManager.deleteRule(ruleId);
  await rulesManager.saveDraft(updated.project_id);
  refresh();
}

export async function addCustomRuleCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void,
  suggestedRule?: ArchitecturalRule
): Promise<void> {
  const currentConfig = state.getRulesConfig();
  if (!currentConfig) {
    vscode.window.showWarningMessage('No project selected. Use ArchAssistant: Start first.');
    return;
  }

  const rule = suggestedRule
    ? {
        ...suggestedRule,
        id: `${suggestedRule.id}_custom_${Date.now()}`,
        suggested: false,
        enabled: true
      }
    : await editor.createRule(currentConfig.project_id);

  if (!rule) return;

  const updated = await rulesManager.addCustomRule(rule);
  await rulesManager.saveDraft(updated.project_id);
  refresh();
}