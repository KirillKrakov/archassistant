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
    await this.state.setRulesConfig(config);
    return config;
  }

  getDraftConfig(): RulesConfig | null {
    return this.state.getRulesConfig();
  }

  async loadActualRules(projectId: string): Promise<RulesConfig> {
    const loaded = await this.backend.getRules(projectId);
    await this.state.setRulesConfig(loaded);
    return loaded;
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
    const config = this.state.getRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded');
    }

    const normalized = this.normalizeConfigForSave({
      ...config,
      project_id: projectId,
      updated_at: new Date().toISOString()
    });

    await this.backend.saveRules(projectId, normalized);
    await this.state.setRulesConfig(normalized);
  }

  async toggleRule(ruleId: string): Promise<RulesConfig> {
    return this.updateRule(ruleId, (rule) => ({ ...rule, enabled: !rule.enabled }));
  }

  async deleteRule(ruleId: string): Promise<RulesConfig> {
    const config = this.mustHaveConfig();

    const updated: RulesConfig = {
      ...config,
      rules: config.rules.filter((rule) => rule.id !== ruleId),
      updated_at: new Date().toISOString()
    };

    await this.state.setRulesConfig(updated);
    return updated;
  }

  async addCustomRule(rule: ArchitecturalRule): Promise<RulesConfig> {
    const config = this.mustHaveConfig();
    const normalizedRule = this.normalizeRuleForSave({
      ...rule,
      suggested: false,
      enabled: true
    });

    const duplicates = config.rules.some((r) => ruleKey(this.normalizeRuleForSave(r)) === ruleKey(normalizedRule));
    if (duplicates) {
      throw new Error(`Rule already exists: ${normalizedRule.name}`);
    }

    const updated: RulesConfig = {
      ...config,
      rules: [...config.rules, normalizedRule],
      updated_at: new Date().toISOString()
    };

    await this.state.setRulesConfig(updated);
    return updated;
  }

  async updateRule(
    ruleId: string,
    updater: (rule: ArchitecturalRule) => ArchitecturalRule
  ): Promise<RulesConfig> {
    const config = this.mustHaveConfig();

    const updated: RulesConfig = {
      ...config,
      rules: config.rules.map((rule) => (rule.id === ruleId ? updater(rule) : rule)),
      updated_at: new Date().toISOString()
    };

    await this.state.setRulesConfig(updated);
    return updated;
  }

  async replaceRulesWithActual(projectId: string): Promise<RulesConfig> {
    const loaded = await this.loadActualRules(projectId);
    return loaded;
  }

  private mustHaveConfig(): RulesConfig {
    const config = this.state.getRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded. Use Start/Configure Project first.');
    }
    return config;
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
    if (isBlank(rule.id)) {
      throw new Error('Rule is missing an id');
    }
    if (isBlank(rule.name)) {
      throw new Error(`Rule ${rule.id} is missing a name`);
    }
    if (isBlank(rule.from_package)) {
      throw new Error(`Rule ${rule.id} is missing from_package`);
    }
    if (!rule.type) {
      throw new Error(`Rule ${rule.id} is missing type`);
    }
    if (!rule.constraint) {
      throw new Error(`Rule ${rule.id} is missing constraint`);
    }

    return {
      ...rule,
      name: rule.name.trim(),
      description: rule.description ?? null,
      to_package: rule.to_package ?? null,
      to_packages: rule.to_packages ?? null,
      pattern: rule.pattern ?? null,
      annotation: rule.annotation ?? null,
      from_selector_mode: rule.from_selector_mode ?? undefined,
      to_selector_mode: rule.to_selector_mode ?? undefined,
      from_class_type: rule.from_class_type ?? null,
      to_class_type: rule.to_class_type ?? null,
      from_layer_type: rule.from_layer_type ?? null,
      to_layer_type: rule.to_layer_type ?? null,
      from_name_pattern: rule.from_name_pattern ?? null,
      to_name_pattern: rule.to_name_pattern ?? null,
      from_method_name_pattern: rule.from_method_name_pattern ?? null,
      to_method_name_pattern: rule.to_method_name_pattern ?? null,
      from_field_name_pattern: rule.from_field_name_pattern ?? null,
      to_field_name_pattern: rule.to_field_name_pattern ?? null,
      from_return_type: rule.from_return_type ?? null,
      to_return_type: rule.to_return_type ?? null,
      from_parameter_types: rule.from_parameter_types ?? null,
      to_parameter_types: rule.to_parameter_types ?? null,
      from_throws_types: rule.from_throws_types ?? null,
      to_throws_types: rule.to_throws_types ?? null,
      from_modifiers: rule.from_modifiers ?? null,
      to_modifiers: rule.to_modifiers ?? null,
      from_field_type: rule.from_field_type ?? null,
      to_field_type: rule.to_field_type ?? null,
      slice_pattern: rule.slice_pattern ?? null,
      max_cycle_length: rule.max_cycle_length ?? null,
      weight: Number.isFinite(rule.weight) ? rule.weight : 1,
      enabled: Boolean(rule.enabled),
      suggested: Boolean(rule.suggested)
    };
  }
}