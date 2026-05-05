import { BackendClient } from '../backend/BackendClient';
import {
  ArchitecturalRule,
  RulesConfig,
  WorkspaceModuleSuggestions
} from '../backend/types';
import { ExtensionState } from '../state/ExtensionState';
import { toBackendProjectPath } from '../utils/projectPaths';
import { ruleKey } from '../utils/helpers';

export class RulesManager {
  constructor(
    private readonly backend: BackendClient,
    private readonly state: ExtensionState
  ) {}

  getDraftConfig(): RulesConfig | null {
    return this.state.getRulesConfig();
  }

  async loadFromBackend(projectId: string): Promise<RulesConfig> {
    const loaded = await this.backend.getRules(projectId);
    await this.state.setRulesConfig(loaded);
    return loaded;
  }

  async suggestAndMerge(
    projectId: string,
    hostProjectPath: string
  ): Promise<RulesConfig> {
    const backendProjectPath = toBackendProjectPath(hostProjectPath);

    const [existing, suggestions] = await Promise.all([
      this.safeLoadConfig(projectId),
      this.backend.suggestRules(projectId, backendProjectPath)
    ]);

    const mergedRules = this.state
      .mergeSuggestedRules(existing.rules, suggestions)
      .sort((a, b) =>
        a.suggested === b.suggested
          ? a.name.localeCompare(b.name)
          : Number(b.suggested) - Number(a.suggested)
      );

    const merged: RulesConfig = {
      ...existing,
      project_id: projectId,
      project_path: backendProjectPath,
      rules: mergedRules,
      updated_at: new Date().toISOString()
    };

    await this.state.setSuggestions(suggestions);
    await this.state.setRulesConfig(merged);
    return merged;
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

    const normalized: RulesConfig = {
      ...config,
      project_id: projectId,
      project_path: toBackendProjectPath(config.project_path ?? ''),
      updated_at: new Date().toISOString()
    };

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
    const key = ruleKey(rule);
    const duplicates = config.rules.filter((r) => ruleKey(r) === key);

    if (duplicates.length > 0) {
      throw new Error(`Rule already exists: ${rule.name}`);
    }

    const updated: RulesConfig = {
      ...config,
      rules: [...config.rules, { ...rule, suggested: false }],
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

  private mustHaveConfig(): RulesConfig {
    const config = this.state.getRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded. Use Start/Configure Project first.');
    }
    return config;
  }

  private async safeLoadConfig(projectId: string): Promise<RulesConfig> {
    try {
      return await this.backend.getRules(projectId);
    } catch {
      return {
        project_id: projectId,
        rules: []
      };
    }
  }
}