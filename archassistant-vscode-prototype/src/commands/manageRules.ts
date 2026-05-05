import * as vscode from 'vscode';
import { ArchitecturalRule } from '../backend/types';
import { ExtensionState } from '../state/ExtensionState';
import { Logger } from '../utils/logger';
import { RulesManager } from '../services/RulesManager';
import { RuleEditor } from '../services/RuleEditor';

export async function toggleRuleCommand(
  ruleId: string,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  await rulesManager.toggleRule(ruleId);
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

  const updated = await editor.editRule(rule);
  if (!updated) return;

  await rulesManager.updateRule(ruleId, () => updated);
  refresh();
}

export async function deleteRuleCommand(
  ruleId: string,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  const confirmed = await vscode.window.showWarningMessage(
    `Delete rule ${ruleId}?`,
    { modal: true },
    'Delete'
  );

  if (confirmed !== 'Delete') return;

  await rulesManager.deleteRule(ruleId);
  refresh();
}

export async function addCustomRuleCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void,
  suggestedRule?: ArchitecturalRule
): Promise<void> {
  const current = state.getCurrentProject();
  if (!current) {
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
    : await editor.createRule(current.projectId);

  if (!rule) return;

  await rulesManager.addCustomRule(rule);
  refresh();
}
