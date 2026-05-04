import * as vscode from 'vscode';

export interface ProjectRegistryEntry {
  projectId: string;
  projectPath: string;
  lastAccessed: string;
  rulesCount: number;
}

export interface LastGenerationCache {
  projectId: string;
  code: string;
  timestamp: string;
  strategy: string;
  score: number | null;
}

export class StorageManager {
  private static instance: StorageManager;

  private constructor(private readonly context: vscode.ExtensionContext) {}

  static getInstance(context: vscode.ExtensionContext): StorageManager {
    if (!StorageManager.instance) {
      StorageManager.instance = new StorageManager(context);
    }
    return StorageManager.instance;
  }

  getBackendUrl(): string {
    return this.context.globalState.get<string>('archassistant.backendUrl') || 'http://localhost:8080';
  }

  async setBackendUrl(url: string): Promise<void> {
    await this.context.globalState.update('archassistant.backendUrl', url);
  }

  getProjectRegistry(): ProjectRegistryEntry[] {
    return this.context.globalState.get<ProjectRegistryEntry[]>('archassistant.projectRegistry') || [];
  }

  async setProjectRegistry(registry: ProjectRegistryEntry[]): Promise<void> {
    await this.context.globalState.update('archassistant.projectRegistry', registry);
  }

  async addOrUpdateProject(entry: ProjectRegistryEntry): Promise<void> {
    const registry = this.getProjectRegistry();
    const index = registry.findIndex(p => p.projectId === entry.projectId);
    if (index >= 0) {
      registry[index] = entry;
    } else {
      registry.push(entry);
    }
    await this.setProjectRegistry(registry);
  }

  getCurrentProjectId(): string | null {
    return this.context.workspaceState.get<string>('archassistant.currentProjectId') || null;
  }

  async setCurrentProjectId(projectId: string | null): Promise<void> {
    await this.context.workspaceState.update('archassistant.currentProjectId', projectId);
  }

  getCurrentProjectPath(): string | null {
    return this.context.workspaceState.get<string>('archassistant.currentProjectPath') || null;
  }

  async setCurrentProjectPath(projectPath: string | null): Promise<void> {
    await this.context.workspaceState.update('archassistant.currentProjectPath', projectPath);
  }

  getLastGenerationCache(): LastGenerationCache | null {
    return this.context.workspaceState.get<LastGenerationCache>('archassistant.lastGenerationCache') || null;
  }

  async setLastGenerationCache(cache: LastGenerationCache | null): Promise<void> {
    await this.context.workspaceState.update('archassistant.lastGenerationCache', cache);
  }
}