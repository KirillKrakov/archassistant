import { BackendClient } from '../backend/BackendClient';
import {
  ArchitecturalRule,
  RulesConfig,
  WorkspaceModuleSuggestions
} from '../backend/types';
import { ExtensionState } from '../state/ExtensionState';
import { toBackendProjectPath } from '../utils/projectPaths';

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

  async suggestAndMerge(projectId: string, hostProjectPath: string): Promise<RulesConfig> {
    const backendProjectPath = toBackendProjectPath(hostProjectPath);

    const [existing, suggestions] = await Promise.all([
      this.safeLoadConfig(projectId),
      this.backend.suggestRules(projectId, backendProjectPath)
    ]);

    const mergedRules = this.mergeBySemanticKey(existing.rules, suggestions)
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

  async refreshSuggestions(projectId: string, hostProjectPath: string): Promise<WorkspaceModuleSuggestions[]> {
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
    const updated: RulesConfig = {
      ...config,
      rules: [...config.rules.filter((r) => r.id !== rule.id), rule],
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

  private mergeBySemanticKey(
    existing: ArchitecturalRule[],
    suggestions: WorkspaceModuleSuggestions[]
  ): ArchitecturalRule[] {
    const merged: ArchitecturalRule[] = [...existing];
    const seen = new Set(existing.map((rule) => this.semanticKey(rule)));

    for (const module of suggestions) {
      for (const rule of module.rules) {
        const key = this.semanticKey(rule);
        if (!seen.has(key)) {
          merged.push(rule);
          seen.add(key);
        }
      }
    }

    return merged;
  }

  private semanticKey(rule: ArchitecturalRule): string {
    return [
      rule.type,
      rule.constraint,
      rule.from_package,
      rule.to_package ?? '',
      rule.to_packages?.join(',') ?? '',
      rule.pattern ?? '',
      rule.annotation ?? '',
      rule.from_selector_mode ?? '',
      rule.to_selector_mode ?? '',
      rule.from_class_type ?? '',
      rule.to_class_type ?? '',
      rule.from_layer_type ?? '',
      rule.to_layer_type ?? ''
    ].join('|');
  }

  private mustHaveConfig(): RulesConfig {
    const config = this.state.getRulesConfig();
    if (!config) {
      throw new Error('No rules config loaded. Use Configure Project first.');
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