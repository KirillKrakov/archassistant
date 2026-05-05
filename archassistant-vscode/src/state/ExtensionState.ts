import * as vscode from 'vscode';
import {
  ArchitecturalRule,
  RulesConfig,
  WorkspaceModuleSuggestions
} from '../backend/types';
import { Storage } from './storage';
import { ruleKey } from '../utils/helpers';

export interface LastGenerationCache {
  projectId: string;
  code: string;
  timestamp: string;
  strategy: string;
  score: number | null;
}

export interface BackendLaunchInfo {
  projectPath: string;
  composeDirectory: string;
  serviceName: string;
  backendUrl: string;
}

export class ExtensionState {
  private readonly backendUrl: Storage<string>;
  private readonly rulesConfig: Storage<RulesConfig | null>;
  private readonly suggestions: Storage<WorkspaceModuleSuggestions[]>;
  private readonly backendStarted: Storage<boolean>;
  private readonly backendLaunchInfo: Storage<BackendLaunchInfo | null>;
  private readonly lastGeneration: Storage<LastGenerationCache | null>;

  constructor(private readonly memento: vscode.Memento) {
    this.backendUrl = new Storage('archassistant.backendUrl', this.memento);
    this.rulesConfig = new Storage('archassistant.rulesConfig', this.memento);
    this.suggestions = new Storage('archassistant.suggestions', this.memento);
    this.backendStarted = new Storage('archassistant.backendStarted', this.memento);
    this.backendLaunchInfo = new Storage('archassistant.backendLaunchInfo', this.memento);
    this.lastGeneration = new Storage('archassistant.lastGeneration', this.memento);
  }

  getBackendUrl(): string {
    return this.backendUrl.get('http://localhost:8080');
  }

  async setBackendUrl(url: string): Promise<void> {
    await this.backendUrl.set(url);
  }

  getRulesConfig(): RulesConfig | null {
    return this.rulesConfig.get(null);
  }

  async setRulesConfig(config: RulesConfig | null): Promise<void> {
    await this.rulesConfig.set(config);
  }

  getSuggestions(): WorkspaceModuleSuggestions[] {
    return this.suggestions.get([]);
  }

  async setSuggestions(items: WorkspaceModuleSuggestions[]): Promise<void> {
    await this.suggestions.set(items);
  }

  async clearSuggestions(): Promise<void> {
    await this.suggestions.clear();
  }

  isBackendStarted(): boolean {
    return this.backendStarted.get(false);
  }

  async setBackendStarted(value: boolean): Promise<void> {
    await this.backendStarted.set(value);
  }

  getBackendLaunchInfo(): BackendLaunchInfo | null {
    return this.backendLaunchInfo.get(null);
  }

  async setBackendLaunchInfo(info: BackendLaunchInfo | null): Promise<void> {
    await this.backendLaunchInfo.set(info);
  }

  getLastGenerationCache(): LastGenerationCache | null {
    return this.lastGeneration.get(null);
  }

  async setLastGenerationCache(cache: LastGenerationCache | null): Promise<void> {
    await this.lastGeneration.set(cache);
  }

  async resetProjectData(): Promise<void> {
    await Promise.all([
      this.setRulesConfig(null),
      this.clearSuggestions(),
      this.setLastGenerationCache(null)
    ]);
  }

  mergeSuggestedRules(
    existing: ArchitecturalRule[],
    suggestions: WorkspaceModuleSuggestions[]
  ): ArchitecturalRule[] {
    const merged = [...existing];
    const seen = new Set(existing.map((rule) => ruleKey(rule)));

    for (const module of suggestions) {
      for (const rule of module.rules) {
        const key = ruleKey(rule);
        if (!seen.has(key)) {
          merged.push(rule);
          seen.add(key);
        }
      }
    }

    return merged;
  }
}