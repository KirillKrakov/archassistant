import { BackendClient } from '../backend/BackendClient';
import {
  ArchitecturalRule,
  RulesConfig,
  WorkspaceModuleSuggestions
} from '../backend/types';
import { ExtensionState } from '../state/ExtensionState';
import { toBackendProjectPath } from '../utils/projectPaths';
import { isBlank, ruleKey } from '../utils/helpers';

export class RulesManager {
  constructor(
    private readonly backend: BackendClient,
    private readonly state: ExtensionState
  ) {}

  createEmptyConfig(projectId: string, hostProjectPath: string): RulesConfig {
    return {
      project_id: projectId,
      project_path: toBackendProjectPath(hostProjectPath),
      rules: []
    };
  }

  async prepareProject(projectId: string, hostProjectPath: string): Promise<RulesConfig> {
    const config = this.createEmptyConfig(projectId, hostProjectPath);
    await this.state.setDraftRulesConfig(config);
    await this.state.setSavedRulesConfig(config);
    return config;
  }

  getDraftConfig(): RulesConfig | null {
    return this.state.getDraftRulesConfig();
  }

  getSavedConfig(): RulesConfig | null {
    return this.state.getSavedRulesConfig();
  }

  async loadActualRules(projectId: string): Promise<RulesConfig> {
    const loaded = await this.backend.getRules(projectId);
    const normalized = this.normalizeConfigForStorage(loaded);
    await this.state.setSavedRulesConfig(normalized);
    await this.state.setDraftRulesConfig(normalized);
    return normalized;
  }

  async refreshSuggestions(
    projectId: string,
    hostProjectPath: string
  ): Promise<WorkspaceModuleSuggestions[]> {
    const backendProjectPath = toBackendProjectPath(hostProjectPath);
    const suggestions = await this.backend.suggestRules(projectId, backendProjectPath);
    await this.state.setSuggestions(suggestions);
    return suggestions;
  }

  async saveDraft(projectId: string): Promise<void> {
    const config = this.state.getDraftRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded');
    }

    const normalized = this.normalizeConfigForSave({
      ...config,
      project_id: projectId,
      updated_at: new Date().toISOString()
    });

    await this.backend.saveRules(projectId, normalized);
    await this.state.setSavedRulesConfig(normalized);
    await this.state.setDraftRulesConfig(normalized);
  }

  async updateDraftRule(ruleId: string, updater: (rule: ArchitecturalRule) => ArchitecturalRule): Promise<RulesConfig> {
    const config = this.mustHaveDraftConfig();
    const updated: RulesConfig = {
      ...config,
      rules: config.rules.map((rule) => (rule.id === ruleId ? updater(rule) : rule)),
      updated_at: new Date().toISOString()
    };

    await this.state.setDraftRulesConfig(updated);
    return updated;
  }

  async removeDraftRule(ruleId: string): Promise<RulesConfig> {
    const config = this.mustHaveDraftConfig();
    const updated: RulesConfig = {
      ...config,
      rules: config.rules.filter((rule) => rule.id !== ruleId),
      updated_at: new Date().toISOString()
    };
    await this.state.setDraftRulesConfig(updated);
    return updated;
  }

  async addCustomRule(rule: ArchitecturalRule): Promise<RulesConfig> {
    const config = this.mustHaveDraftConfig();
    const normalizedRule = this.normalizeRuleForSave({
      ...rule,
      suggested: false
    });

    const duplicate = config.rules.some((r) => ruleKey(this.normalizeRuleForSave(r)) === ruleKey(normalizedRule));
    if (duplicate) {
      throw new Error(`Rule already exists: ${normalizedRule.name}`);
    }

    const updated: RulesConfig = {
      ...config,
      rules: [...config.rules, normalizedRule],
      updated_at: new Date().toISOString()
    };

    await this.state.setDraftRulesConfig(updated);
    return updated;
  }

  async replaceDraftWithActual(projectId: string): Promise<RulesConfig> {
    return this.loadActualRules(projectId);
  }

  getSuggestedRulesExcludingSaved(): WorkspaceModuleSuggestions[] {
    const draft = this.state.getDraftRulesConfig();
    const savedKeys = new Set((draft?.rules ?? []).map((rule) => ruleKey(this.normalizeRuleForSave(rule))));

    return this.state.getSuggestions()
      .map((module) => ({
        ...module,
        rules: module.rules.filter((rule) => !savedKeys.has(ruleKey(this.normalizeRuleForSave(rule))))
      }))
      .filter((module) => module.rules.length > 0);
  }

  private mustHaveDraftConfig(): RulesConfig {
    const config = this.state.getDraftRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded. Use Start/Configure Project first.');
    }
    return config;
  }

  private normalizeConfigForStorage(config: RulesConfig): RulesConfig {
    return {
      ...config,
      rules: config.rules.map((rule) => this.normalizeRuleForSave(rule))
    };
  }

  private normalizeConfigForSave(config: RulesConfig): RulesConfig {
    const deduped = new Map<string, ArchitecturalRule>();

    for (const rule of config.rules) {
      const normalized = this.normalizeRuleForSave(rule);
      const key = ruleKey(normalized);
      if (!deduped.has(key)) {
        deduped.set(key, normalized);
      }
    }

    return {
      ...config,
      rules: [...deduped.values()]
    };
  }

  private normalizeRuleForSave(rule: ArchitecturalRule): ArchitecturalRule {
    const id = typeof rule.id === 'string' ? rule.id.trim() : '';
    const name = typeof rule.name === 'string' ? rule.name.trim() : '';
    const fromPackage = typeof rule.from_package === 'string' ? rule.from_package.trim() : '';

    if (!id) {
      throw new Error('Rule is missing an id');
    }
    if (!name) {
      throw new Error(`Rule ${id} is missing a name`);
    }
    if (!fromPackage) {
      throw new Error(`Rule ${id} is missing from_package`);
    }
    if (!rule.type) {
      throw new Error(`Rule ${id} is missing type`);
    }
    if (!rule.constraint) {
      throw new Error(`Rule ${id} is missing constraint`);
    }

    const trimText = (value: unknown): string | null => {
      if (typeof value !== 'string') return null;
      const v = value.trim();
      return v ? v : null;
    };

    const trimList = (value: unknown): string[] | null => {
      if (!Array.isArray(value)) return null;
      const list = value
        .map((item) => (typeof item === 'string' ? item.trim() : ''))
        .filter(Boolean);
      return list.length > 0 ? list : null;
    };

    return {
      ...rule,
      id,
      name,
      from_package: fromPackage,
      description: trimText(rule.description),
      to_package: trimText(rule.to_package),
      to_packages: trimList(rule.to_packages),
      pattern: trimText(rule.pattern),
      annotation: trimText(rule.annotation),
      from_selector_mode: rule.from_selector_mode,
      to_selector_mode: rule.to_selector_mode,
      from_class_type: rule.from_class_type ?? null,
      to_class_type: rule.to_class_type ?? null,
      from_layer_type: rule.from_layer_type ?? null,
      to_layer_type: rule.to_layer_type ?? null,
      from_name_pattern: trimText(rule.from_name_pattern),
      to_name_pattern: trimText(rule.to_name_pattern),
      from_method_name_pattern: trimText(rule.from_method_name_pattern),
      to_method_name_pattern: trimText(rule.to_method_name_pattern),
      from_field_name_pattern: trimText(rule.from_field_name_pattern),
      to_field_name_pattern: trimText(rule.to_field_name_pattern),
      from_return_type: trimText(rule.from_return_type),
      to_return_type: trimText(rule.to_return_type),
      from_parameter_types: trimList(rule.from_parameter_types),
      to_parameter_types: trimList(rule.to_parameter_types),
      from_throws_types: trimList(rule.from_throws_types),
      to_throws_types: trimList(rule.to_throws_types),
      from_modifiers: trimList(rule.from_modifiers),
      to_modifiers: trimList(rule.to_modifiers),
      from_field_type: trimText(rule.from_field_type),
      to_field_type: trimText(rule.to_field_type),
      slice_pattern: trimText(rule.slice_pattern),
      max_cycle_length:
        typeof rule.max_cycle_length === 'number' && Number.isFinite(rule.max_cycle_length)
          ? rule.max_cycle_length
          : null,
      weight: Number.isFinite(rule.weight) ? rule.weight : 1,
      enabled: Boolean(rule.enabled),
      suggested: Boolean(rule.suggested)
    };
  }
}