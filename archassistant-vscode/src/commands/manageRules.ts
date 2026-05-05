import * as vscode from 'vscode';
import { ArchitecturalRule } from '../backend/types';
import { ProjectRegistry } from '../state/projectRegistry';
import { RulesManager } from '../services/RulesManager';
import { RuleEditor } from '../services/RuleEditor';
import { ExtensionState } from '../state/ExtensionState';

async function pickSavedRule(state: ExtensionState, title: string): Promise<ArchitecturalRule | null> {
  const config = state.getDraftRulesConfig();
  const rules = config?.rules ?? [];
  if (rules.length === 0) {
    vscode.window.showWarningMessage('Saved rules are empty. Use Get Actual Rules first.');
    return null;
  }

  const picked = await vscode.window.showQuickPick(
    rules.map((rule) => ({
      label: rule.name,
      description: `${rule.type} · ${rule.constraint}`,
      rule
    })),
    {
      title,
      placeHolder: title
    }
  );

  return picked?.rule ?? null;
}

export async function editRuleCommand(
  ruleId: string | undefined,
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void
): Promise<void> {
  let rule = ruleId
    ? state.getDraftRulesConfig()?.rules.find((item) => item.id === ruleId)
    : null;

  if (!rule) {
    rule = await pickSavedRule(state, 'Select a rule to edit');
  }

  if (!rule) return;

  const updatedRule = await editor.editRule(rule);
  if (!updatedRule) return;

  await rulesManager.updateDraftRule(rule.id, () => updatedRule);
  refresh();
}

export async function deleteRuleCommand(
  ruleId: string | undefined,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  let rule = ruleId
    ? state.getDraftRulesConfig()?.rules.find((item) => item.id === ruleId)
    : null;

  if (!rule) {
    rule = await pickSavedRule(state, 'Select a rule to delete');
  }

  if (!rule) return;

  const confirmed = await vscode.window.showWarningMessage(
    `Delete rule "${rule.name}"?`,
    { modal: true },
    'Delete'
  );

  if (confirmed !== 'Delete') return;

  await rulesManager.removeDraftRule(rule.id);
  refresh();
}

export async function addCustomRuleCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void,
  suggestedRule?: ArchitecturalRule
): Promise<void> {
  const currentConfig = state.getDraftRulesConfig();
  if (!currentConfig) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  let rule: ArchitecturalRule | null = suggestedRule ?? null;

  if (!rule) {
    const savedRules = currentConfig.rules;
    const savedKeys = new Set(savedRules.map((r) => r.id));

    const availableSuggestions = state.getSuggestions()
      .flatMap((module) => module.rules)
      .filter((s) => !savedKeys.has(s.id));

    const quickPickItems = [
      ...availableSuggestions.map((s) => ({
        label: s.name,
        description: `${s.type} · ${s.constraint}`,
        rule: s
      })),
      {
        label: 'Create Manual Custom Rule',
        description: 'Open rule editor',
        rule: null as ArchitecturalRule | null
      }
    ];

    const picked = await vscode.window.showQuickPick(quickPickItems, {
      title: 'Add custom rule',
      placeHolder: 'Choose a suggested rule or create one manually'
    });

    if (!picked) return;
    rule = picked.rule ?? (await editor.createRule(currentConfig.project_id));
  }

  if (!rule) return;

  await rulesManager.addCustomRule(rule);
  refresh();
}