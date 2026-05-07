import * as vscode from 'vscode';
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml';
import {
  ArchitecturalRule,
  ClassType,
  ConstraintType,
  LayerType,
  ProjectProfile,
  RuleType,
  RulesConfig,
  SelectorMode,
  Severity
} from '../backend/types';
import { ExtensionState } from '../state/ExtensionState';
import { ProjectRegistry } from '../state/projectRegistry';
import { RulesManager } from '../services/RulesManager';
import { Logger } from '../utils/logger';
import { RulesTreeDataProvider } from '../ui/sidebar/RulesTreeDataProvider';
import { ArchAssistantSidebarProvider } from '../ui/sidebar/ArchAssistantSidebarProvider';
import { toBackendProjectPath } from '../utils/projectPaths';

type ImportedRulesDocument = {
  version?: string;
  project_id?: string;
  project_type?: string;
  settings?: RulesConfig['settings'] | Record<string, unknown> | null;
  rules?: unknown;
  project_path?: string;
  created_at?: string;
  updated_at?: string;
};

const ruleTypeSet = new Set<string>(Object.values(RuleType));
const constraintTypeSet = new Set<string>(Object.values(ConstraintType));
const selectorModeSet = new Set<string>(Object.values(SelectorMode));
const severitySet = new Set<string>(Object.values(Severity));
const classTypeSet = new Set<string>(Object.values(ClassType));
const layerTypeSet = new Set<string>(Object.values(LayerType));
const projectProfileSet = new Set<string>(Object.values(ProjectProfile));

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function trimToString(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function stringOrFallback(value: unknown, fallback: string): string {
  const trimmed = trimToString(value);
  return trimmed ?? fallback;
}

function booleanOrFallback(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (normalized === 'true') return true;
    if (normalized === 'false') return false;
  }
  if (typeof value === 'number') {
    if (value === 1) return true;
    if (value === 0) return false;
  }
  return fallback;
}

function numberOrFallback(value: unknown, fallback: number): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

function enumOrNull(value: unknown, allowed: Set<string>): string | null {
  const trimmed = trimToString(value);
  if (!trimmed) return null;
  return allowed.has(trimmed) ? trimmed : null;
}

function stringListOrNull(value: unknown): string[] | null {
  if (Array.isArray(value)) {
    const items = value
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter((item) => item.length > 0);
    return items.length > 0 ? items : null;
  }

  if (typeof value === 'string') {
    const items = value
      .split(/\r?\n|,/g)
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
    return items.length > 0 ? items : null;
  }

  return null;
}

function defaultExportConfig(
  projectId: string,
  projectPath: string,
  source?: RulesConfig | null
): RulesConfig {
  return {
    version: source?.version ?? '2.0',
    project_id: projectId,
    project_type: source?.project_type ?? null,
    settings: source?.settings ?? null,
    rules: source?.rules ?? [],
    project_path: projectPath,
    created_at: source?.created_at ?? null,
    updated_at: source?.updated_at ?? null
  };
}

function parseRulesFromDocument(parsed: unknown): {
  rules: unknown[];
  meta: ImportedRulesDocument;
} | null {
  if (Array.isArray(parsed)) {
    return { rules: parsed, meta: {} };
  }

  if (isRecord(parsed)) {
    const rules = parsed.rules;
    if (!Array.isArray(rules)) {
      return null;
    }

    return {
      rules,
      meta: parsed as ImportedRulesDocument
    };
  }

  return null;
}

function normalizeImportedRule(raw: unknown, index: number): {
  rule: ArchitecturalRule | null;
  error?: string;
} {
  if (!isRecord(raw)) {
    return { rule: null, error: `Rule #${index + 1} is not an object.` };
  }

const id = trimToString(raw.id);
const name = trimToString(raw.name);
const type = enumOrNull(raw.type, ruleTypeSet);
const fromPackage = trimToString(raw.from_package);
const constraint = enumOrNull(raw.constraint, constraintTypeSet);
const fromSelectorMode = enumOrNull(raw.from_selector_mode, selectorModeSet);
const toSelectorMode = enumOrNull(raw.to_selector_mode, selectorModeSet);
const severity = enumOrNull(raw.severity, severitySet);

    const errors: string[] = [];
    if (!id) errors.push('id');
    if (!name) errors.push('name');
    if (!type) errors.push('type');
    if (!fromPackage) errors.push('from_package');
    if (!constraint) errors.push('constraint');
    if (!fromSelectorMode) errors.push('from_selector_mode');
    if (!toSelectorMode) errors.push('to_selector_mode');
    if (!severity) errors.push('severity');

    if (errors.length > 0) {
    return {
        rule: null,
        error: `Rule #${index + 1} is missing/invalid required field(s): ${errors.join(', ')}.`
    };
    }

    return {
    rule: {
        id: id ?? '',
        name: name ?? '',
        description: trimToString(raw.description),
        type: type as RuleType,
        from_package: fromPackage ?? '',
      to_package: trimToString(raw.to_package),
      to_packages: stringListOrNull(raw.to_packages),
      constraint: constraint as ConstraintType,
      pattern: trimToString(raw.pattern),
      annotation: trimToString(raw.annotation),
      from_selector_mode: fromSelectorMode as SelectorMode,
      to_selector_mode: toSelectorMode as SelectorMode,
      from_class_type: enumOrNull(raw.from_class_type, classTypeSet) as ClassType | null,
      to_class_type: enumOrNull(raw.to_class_type, classTypeSet) as ClassType | null,
      from_layer_type: enumOrNull(raw.from_layer_type, layerTypeSet) as LayerType | null,
      to_layer_type: enumOrNull(raw.to_layer_type, layerTypeSet) as LayerType | null,
      from_name_pattern: trimToString(raw.from_name_pattern),
      to_name_pattern: trimToString(raw.to_name_pattern),
      from_method_name_pattern: trimToString(raw.from_method_name_pattern),
      to_method_name_pattern: trimToString(raw.to_method_name_pattern),
      from_field_name_pattern: trimToString(raw.from_field_name_pattern),
      to_field_name_pattern: trimToString(raw.to_field_name_pattern),
      from_return_type: trimToString(raw.from_return_type),
      to_return_type: trimToString(raw.to_return_type),
      from_parameter_types: stringListOrNull(raw.from_parameter_types),
      to_parameter_types: stringListOrNull(raw.to_parameter_types),
      from_throws_types: stringListOrNull(raw.from_throws_types),
      to_throws_types: stringListOrNull(raw.to_throws_types),
      from_modifiers: stringListOrNull(raw.from_modifiers),
      to_modifiers: stringListOrNull(raw.to_modifiers),
      from_field_type: trimToString(raw.from_field_type),
      to_field_type: trimToString(raw.to_field_type),
      slice_pattern: trimToString(raw.slice_pattern),
      max_cycle_length: typeof raw.max_cycle_length === 'number' && Number.isFinite(raw.max_cycle_length)
        ? raw.max_cycle_length
        : typeof raw.max_cycle_length === 'string' && raw.max_cycle_length.trim().length > 0
          ? (() => {
              const parsed = Number(raw.max_cycle_length);
              return Number.isFinite(parsed) ? parsed : null;
            })()
          : null,
      severity: severity as Severity,
      weight: numberOrFallback(raw.weight, 1),
      enabled: booleanOrFallback(raw.enabled, true),
      suggested: booleanOrFallback(raw.suggested, false)
    }
  };
}

function defaultConstraintForType(type: string): string {
  switch (type) {
    case RuleType.NAMING_CONVENTION:
      return ConstraintType.NAMING_SUFFIX;
    case RuleType.ANNOTATION_CHECK:
      return ConstraintType.HAS_ANNOTATION;
    case RuleType.DEPENDENCY:
      return ConstraintType.NO_DEPENDENCY;
    case RuleType.LAYER_ISOLATION:
      return ConstraintType.NO_DEPENDENCY;
    case RuleType.CYCLE_CHECK:
      return ConstraintType.NO_CYCLE;
    case RuleType.INHERITANCE_CHECK:
      return ConstraintType.SHOULD_EXTEND;
    case RuleType.INTERFACE_CHECK:
      return ConstraintType.SHOULD_IMPLEMENT;
    case RuleType.MODIFIER_CHECK:
      return ConstraintType.SHOULD_BE_PUBLIC;
    case RuleType.METHOD_SIGNATURE_CHECK:
      return ConstraintType.RETURN_TYPE;
    case RuleType.FIELD_CHECK:
      return ConstraintType.FIELD_TYPE;
    case RuleType.EXCEPTION_CHECK:
      return ConstraintType.SHOULD_NOT_THROW;
    default:
      return ConstraintType.CUSTOM;
  }
}

export async function importRulesYamlCommand(
  state: ExtensionState,
  registry: ProjectRegistry,
  rulesManager: RulesManager,
  sidebarProvider: ArchAssistantSidebarProvider,
  rulesProvider: RulesTreeDataProvider,
  logger: Logger
): Promise<void> {
  const current = registry.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  const selected = await vscode.window.showOpenDialog({
    canSelectFolders: false,
    canSelectFiles: true,
    canSelectMany: false,
    openLabel: 'Import Rules YAML',
    title: 'Select YAML file with rules',
    filters: {
      YAML: ['yaml', 'yml']
    }
  });

  const file = selected?.[0];
  if (!file) return;

  const confirmed = await vscode.window.showWarningMessage(
    'This will replace the current Saved Rules with rules from the YAML file.',
    { modal: true },
    'Replace',
    'Cancel'
  );

  if (confirmed !== 'Replace') return;

  try {
    const content = Buffer.from(await vscode.workspace.fs.readFile(file)).toString('utf8');
    const parsed = parseYaml(content);
    const extracted = parseRulesFromDocument(parsed);

    if (!extracted) {
      throw new Error('YAML root must be either a list of rules or an object with a "rules" array.');
    }

    const importedRules: ArchitecturalRule[] = [];
    const errors: string[] = [];

    extracted.rules.forEach((raw, index) => {
      const result = normalizeImportedRule(raw, index);
      if (result.rule) {
        importedRules.push(result.rule);
      } else if (result.error) {
        errors.push(result.error);
      }
    });

    if (errors.length > 0) {
      throw new Error(`YAML import failed:\n${errors.join('\n')}`);
    }

    const importedConfig: RulesConfig = {
      version: extracted.meta.version ?? '2.0',
      project_id: current.projectId,
      project_type: extracted.meta.project_type ?? null,
      settings: isRecord(extracted.meta.settings)
        ? (extracted.meta.settings as unknown as RulesConfig['settings'])
        : null,
      rules: importedRules,
      project_path: toBackendProjectPath(current.projectPath),
      created_at: extracted.meta.created_at ?? null,
      updated_at: new Date().toISOString()
    };

    await state.setDraftRulesConfig(importedConfig);
    await state.clearSuggestions();

    await rulesManager.saveDraft(current.projectId);

    const savedConfig = state.getDraftRulesConfig();
    const rulesCount = savedConfig?.rules.length ?? importedRules.length;

    await registry.updateRulesCount(current.projectId, rulesCount);
    sidebarProvider.refresh();
    rulesProvider.refresh();

    logger.info(
      'Imported {} rule(s) from YAML into project {}',
      rulesCount,
      current.projectId
    );

    vscode.window.showInformationMessage(`Imported ${rulesCount} rule(s) from YAML.`);
  } catch (error: any) {
    logger.warn('YAML import failed: {}', error.message);
    vscode.window.showErrorMessage(`Failed to import YAML rules: ${error.message}`);
  }
}

export async function exportRulesYamlCommand(
  state: ExtensionState,
  registry: ProjectRegistry,
  logger: Logger
): Promise<void> {
  const current = registry.getCurrentProject();
  if (!current) {
    vscode.window.showWarningMessage('No project selected. Use Start/Configure first.');
    return;
  }

  const config = state.getDraftRulesConfig() ?? state.getSavedRulesConfig() ?? null;
  const exportConfig = defaultExportConfig(current.projectId, current.projectPath, config);

  const saveUri = await vscode.window.showSaveDialog({
    defaultUri: vscode.workspace.workspaceFolders?.[0]
      ? vscode.Uri.joinPath(vscode.workspace.workspaceFolders[0].uri, `archassistant-rules-${current.projectId}.yaml`)
      : vscode.Uri.file(`archassistant-rules-${current.projectId}.yaml`),
    filters: {
      YAML: ['yaml', 'yml']
    },
    saveLabel: 'Export Rules YAML'
  });

  if (!saveUri) return;

  try {
    const yamlText = stringifyYaml(exportConfig, { indent: 2 });
    await vscode.workspace.fs.writeFile(saveUri, Buffer.from(yamlText, 'utf8'));
    logger.info('Exported rules YAML for project {} to {}', current.projectId, saveUri.fsPath);
    vscode.window.showInformationMessage(`Rules exported to ${saveUri.fsPath}`);
  } catch (error: any) {
    logger.warn('YAML export failed: {}', error.message);
    vscode.window.showErrorMessage(`Failed to export YAML rules: ${error.message}`);
  }
}