import * as vscode from 'vscode';
import { ArchitecturalRule } from '../backend/types';
import { RulesManager } from '../services/RulesManager';
import { RuleEditor } from '../services/RuleEditor';
import { ExtensionState } from '../state/ExtensionState';
import { normalizeProjectId } from '../utils/helpers';

type RuleArg =
  | string
  | ArchitecturalRule
  | { id?: string; rule?: ArchitecturalRule }
  | undefined;

function resolveRuleFromArg(state: ExtensionState, arg: RuleArg): ArchitecturalRule | null {
  const draftRules = state.getDraftRulesConfig()?.rules ?? [];
  if (!arg) return null;

  if (typeof arg === 'string') {
    return draftRules.find((rule) => rule.id === arg) ?? null;
  }

  if (typeof arg !== 'object') return null;

  const candidate = arg as Record<string, unknown>;

  if (candidate.rule && typeof candidate.rule === 'object') {
    return resolveRuleFromArg(state, candidate.rule as ArchitecturalRule);
  }

  if (typeof candidate.id === 'string') {
    return draftRules.find((rule) => rule.id === candidate.id) ?? (arg as ArchitecturalRule);
  }

  if (
    typeof candidate.name === 'string' &&
    typeof candidate.type === 'string' &&
    typeof candidate.constraint === 'string' &&
    typeof candidate.from_package === 'string'
  ) {
    return arg as ArchitecturalRule;
  }

  return null;
}

function ensureRuleId(rule: ArchitecturalRule, projectId: string): ArchitecturalRule {
  if (rule.id?.trim()) return rule;

  const safeProjectId = normalizeProjectId(projectId);
  const safeName = normalizeProjectId(rule.name || 'custom_rule');
  return {
    ...rule,
    id: `${safeProjectId}_${safeName}_${Date.now()}`
  };
}

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

export async function toggleRuleCommand(
  ruleArg: RuleArg,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  let rule = resolveRuleFromArg(state, ruleArg);

  if (!rule) {
    rule = await pickSavedRule(state, 'Select a rule to toggle');
  }

  if (!rule) return;

  await rulesManager.updateDraftRule(rule.id, (current) => ({
    ...current,
    enabled: !current.enabled
  }));

  refresh();
}

export async function editRuleCommand(
  ruleArg: RuleArg,
  state: ExtensionState,
  rulesManager: RulesManager,
  editor: RuleEditor,
  refresh: () => void
): Promise<void> {
  let rule = resolveRuleFromArg(state, ruleArg);

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
  ruleArg: RuleArg,
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  let rule = resolveRuleFromArg(state, ruleArg);

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
  suggestedRule?: RuleArg
): Promise<void> {
  const currentConfig = state.getDraftRulesConfig();
  if (!currentConfig) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  let rule = resolveRuleFromArg(state, suggestedRule);

  if (rule) {
    rule = ensureRuleId(rule, currentConfig.project_id);
  }

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

    rule = picked.rule
      ? ensureRuleId(picked.rule, currentConfig.project_id)
      : await editor.createRule(currentConfig.project_id);
  }

  if (!rule) return;

  await rulesManager.addCustomRule(ensureRuleId(rule, currentConfig.project_id));
  refresh();
}