import * as vscode from 'vscode';
import { Storage } from './storage';

export interface StoredProjectInfo {
  projectId: string;
  projectPath: string;
  rulesCount?: number;
  lastAccessed?: string;
}

export class ProjectRegistry {
  private readonly currentProject: Storage<StoredProjectInfo | null>;
  private readonly recentProjects: Storage<StoredProjectInfo[]>;

  constructor(private readonly memento: vscode.Memento) {
    this.currentProject = new Storage('archassistant.currentProject', this.memento);
    this.recentProjects = new Storage('archassistant.recentProjects', this.memento);
  }

  getCurrentProject(): StoredProjectInfo | null {
    return this.currentProject.get(null);
  }

  async setCurrentProject(projectId: string, projectPath: string): Promise<void> {
    const existing = this.getCurrentProject();
    const entry: StoredProjectInfo = {
      projectId,
      projectPath,
      rulesCount: existing?.rulesCount ?? 0,
      lastAccessed: new Date().toISOString()
    };

    await this.currentProject.set(entry);
    await this.upsert(entry);
  }

  list(): StoredProjectInfo[] {
    return this.recentProjects.get([]);
  }

  async upsert(project: StoredProjectInfo): Promise<void> {
    const filtered = this.list().filter((item) => item.projectId !== project.projectId);
    filtered.unshift({
      ...project,
      lastAccessed: project.lastAccessed ?? new Date().toISOString()
    });
    await this.recentProjects.set(filtered.slice(0, 10));
  }

  async updateRulesCount(projectId: string, rulesCount: number): Promise<void> {
    const current = this.getCurrentProject();
    if (current?.projectId === projectId) {
      await this.currentProject.set({ ...current, rulesCount, lastAccessed: new Date().toISOString() });
    }

    const updated = this.list().map((item) =>
      item.projectId === projectId
        ? { ...item, rulesCount, lastAccessed: new Date().toISOString() }
        : item
    );
    await this.recentProjects.set(updated);
  }

  async remove(projectId: string): Promise<void> {
    const filtered = this.list().filter((item) => item.projectId !== projectId);
    await this.recentProjects.set(filtered);

    const current = this.getCurrentProject();
    if (current?.projectId === projectId) {
      await this.currentProject.clear();
    }
  }
}