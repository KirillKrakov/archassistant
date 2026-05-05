import * as vscode from 'vscode';
import { ArchitecturalRule, RulesConfig, WorkspaceModuleSuggestions } from '../backend/types';
import { Storage } from './storage';

export interface StoredProjectInfo {
  projectId: string;
  projectPath: string;
}

export class ExtensionState {
  private readonly currentProject: Storage<StoredProjectInfo | null>;
  private readonly rulesConfig: Storage<RulesConfig | null>;
  private readonly suggestions: Storage<WorkspaceModuleSuggestions[]>;
  private readonly backendStarted: Storage<boolean>;

  constructor(private readonly memento: vscode.Memento) {
    this.currentProject = new Storage<StoredProjectInfo | null>('archassistant.currentProject', this.memento);
    this.rulesConfig = new Storage<RulesConfig | null>('archassistant.rulesConfig', this.memento);
    this.suggestions = new Storage<WorkspaceModuleSuggestions[]>('archassistant.suggestions', this.memento);
    this.backendStarted = new Storage<boolean>('archassistant.backendStarted', this.memento);
  }

  getCurrentProject(): StoredProjectInfo | null {
    return this.currentProject.get(null);
  }

  async setCurrentProject(projectId: string, projectPath: string): Promise<void> {
    await this.currentProject.set({ projectId, projectPath });
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

  mergeSuggestedRules(existing: ArchitecturalRule[], suggestions: WorkspaceModuleSuggestions[]): ArchitecturalRule[] {
    const merged = [...existing];
    const ids = new Set(existing.map((rule) => rule.id));

    for (const module of suggestions) {
      for (const rule of module.rules) {
        if (!ids.has(rule.id)) {
          merged.push(rule);
          ids.add(rule.id);
        }
      }
    }

    return merged;
  }
}
