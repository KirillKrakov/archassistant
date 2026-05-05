import * as vscode from 'vscode';
import { Storage } from './storage';
import { StoredProjectInfo } from './ExtensionState';

export class ProjectRegistry {
  private readonly recentProjects: Storage<StoredProjectInfo[]>;

  constructor(private readonly memento: vscode.Memento) {
    this.recentProjects = new Storage<StoredProjectInfo[]>('archassistant.recentProjects', this.memento);
  }

  list(): StoredProjectInfo[] {
    return this.recentProjects.get([]);
  }

  async upsert(project: StoredProjectInfo): Promise<void> {
    const current = this.list().filter((item) => item.projectId !== project.projectId);
    current.unshift(project);
    await this.recentProjects.set(current.slice(0, 10));
  }
}
