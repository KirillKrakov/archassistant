import { StorageManager, ProjectRegistryEntry } from './storage';

export class ProjectRegistry {
  constructor(private readonly storage: StorageManager) {}

  async getCurrentProject(): Promise<ProjectRegistryEntry | null> {
    const projectId = this.storage.getCurrentProjectId();
    if (!projectId) return null;
    return this.storage.getProjectRegistry().find(p => p.projectId === projectId) || null;
  }

  async setCurrentProject(projectId: string, projectPath: string, rulesCount = 0): Promise<void> {
    await this.storage.setCurrentProjectId(projectId);
    await this.storage.setCurrentProjectPath(projectPath);

    await this.storage.addOrUpdateProject({
      projectId,
      projectPath,
      lastAccessed: new Date().toISOString(),
      rulesCount
    });
  }

  async getAllProjects(): Promise<ProjectRegistryEntry[]> {
    return this.storage.getProjectRegistry();
  }

  async removeProject(projectId: string): Promise<void> {
    const filtered = this.storage.getProjectRegistry().filter(p => p.projectId !== projectId);
    await this.storage.setProjectRegistry(filtered);

    if (this.storage.getCurrentProjectId() === projectId) {
      await this.storage.setCurrentProjectId(null);
      await this.storage.setCurrentProjectPath(null);
    }
  }

  async updateRulesCount(projectId: string, rulesCount: number): Promise<void> {
    const registry = this.storage.getProjectRegistry();
    const entry = registry.find(p => p.projectId === projectId);
    if (!entry) return;

    entry.rulesCount = rulesCount;
    entry.lastAccessed = new Date().toISOString();
    await this.storage.setProjectRegistry(registry);
  }
}