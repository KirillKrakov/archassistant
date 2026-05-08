import * as vscode from 'vscode';
import {
  ArchitecturalRule,
  ConstraintType,
  RuleType,
  SelectorMode,
  Severity
} from '../backend/types';
import { RulesManager } from '../services/RulesManager';
import { ExtensionState } from '../state/ExtensionState';
import { normalizeProjectId, ruleKey } from '../utils/helpers';
import { RuleEditorPanel } from '../ui/webviews/RuleEditorPanel';

type RuleArg = unknown;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function resolveRuleFromArg(state: ExtensionState, arg: RuleArg): ArchitecturalRule | null {
  const draftRules = state.getDraftRulesConfig()?.rules ?? [];
  if (!arg) return null;

  if (typeof arg === 'string') {
    return draftRules.find((rule) => rule.id === arg) ?? null;
  }

  if (!isRecord(arg)) return null;

  if (isRecord(arg.rule)) {
    return resolveRuleFromArg(state, arg.rule);
  }

  const looksLikeRule =
    typeof arg.name === 'string' &&
    typeof arg.type === 'string' &&
    typeof arg.constraint === 'string' &&
    typeof arg.from_package === 'string';

  if (looksLikeRule) {
    return arg as unknown as ArchitecturalRule;
  }

  if (typeof arg.id === 'string') {
    const found = draftRules.find((rule) => rule.id === arg.id);
    if (found) return found;

    if (looksLikeRule) {
      return arg as unknown as ArchitecturalRule;
    }
  }

  return null;
}

function createBlankCustomRule(projectId: string): ArchitecturalRule {
  const safeProjectId = normalizeProjectId(projectId || 'project');

  return {
    id: `${safeProjectId}_custom_${Date.now()}`,
    name: 'Custom rule',
    description: null,
    type: RuleType.CUSTOM,
    from_package: '',
    to_package: null,
    to_packages: null,
    constraint: ConstraintType.CUSTOM,
    pattern: null,
    annotation: null,
    from_selector_mode: SelectorMode.PACKAGE,
    to_selector_mode: SelectorMode.PACKAGE,
    from_class_type: null,
    to_class_type: null,
    from_layer_type: null,
    to_layer_type: null,
    from_name_pattern: null,
    to_name_pattern: null,
    from_method_name_pattern: null,
    to_method_name_pattern: null,
    from_field_name_pattern: null,
    to_field_name_pattern: null,
    from_return_type: null,
    to_return_type: null,
    from_parameter_types: null,
    to_parameter_types: null,
    from_throws_types: null,
    to_throws_types: null,
    from_modifiers: null,
    to_modifiers: null,
    from_field_type: null,
    to_field_type: null,
    slice_pattern: null,
    max_cycle_length: null,
    severity: Severity.WARNING,
    weight: 1,
    enabled: true,
    suggested: false
  };
}

function ensureRuleId(rule: ArchitecturalRule, projectId: string): ArchitecturalRule {
  if (typeof rule.id === 'string' && rule.id.trim()) {
    return rule;
  }

  const safeName =
    typeof rule.name === 'string' && rule.name.trim()
      ? rule.name
      : typeof rule.type === 'string'
        ? rule.type
        : 'custom_rule';

  return {
    ...rule,
    id: `${normalizeProjectId(projectId || 'project')}_${normalizeProjectId(safeName)}_${Date.now()}`
  };
}

function getUnaddedSuggestedRules(state: ExtensionState): ArchitecturalRule[] {
  const draft = state.getDraftRulesConfig();
  const savedRules = draft?.rules ?? [];
  const savedKeys = new Set(savedRules.map((rule) => ruleKey(rule)));

  return state
    .getSuggestions()
    .flatMap((module) => module.rules)
    .filter((rule) => !savedKeys.has(ruleKey(rule)));
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
  refresh: () => void
): Promise<void> {
  let rule = resolveRuleFromArg(state, ruleArg);

  if (!rule) {
    rule = await pickSavedRule(state, 'Select a rule to edit');
  }

  if (!rule) return;

  const updatedRule = await RuleEditorPanel.open(rule, 'Edit Rule');
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

export async function addSuggestedRuleCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void,
  suggestedRule?: RuleArg
): Promise<void> {
  const currentConfig = state.getDraftRulesConfig();
  if (!currentConfig) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  let rule = resolveRuleFromArg(state, suggestedRule);

  if (!rule) {
    const availableSuggestions = getUnaddedSuggestedRules(state);

    if (availableSuggestions.length === 0) {
      vscode.window.showInformationMessage('No suggested rules are available.');
      return;
    }

    const picked = await vscode.window.showQuickPick(
      availableSuggestions.map((item) => ({
        label: item.name,
        description: `${item.type} · ${item.constraint}`,
        rule: item
      })),
      {
        title: 'Add Suggested Rule',
        placeHolder: 'Choose a suggested rule to add'
      }
    );

    if (!picked) return;
    rule = picked.rule;
  }

  rule = ensureRuleId(rule, currentConfig.project_id);
  await rulesManager.addCustomRule(rule);
  refresh();
}

export async function addCustomRuleCommand(
  state: ExtensionState,
  rulesManager: RulesManager,
  refresh: () => void
): Promise<void> {
  const currentConfig = state.getDraftRulesConfig();
  if (!currentConfig) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  const rule = await RuleEditorPanel.open(
    createBlankCustomRule(currentConfig.project_id),
    'Create Custom Rule'
  );

  if (!rule) return;

  await rulesManager.addCustomRule(ensureRuleId(rule, currentConfig.project_id));
  refresh();
}