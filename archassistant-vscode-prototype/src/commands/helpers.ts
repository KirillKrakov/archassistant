import * as vscode from 'vscode';
import { projectIdFromPath } from '../utils/helpers';
import { ExtensionState } from '../state/ExtensionState';

export async function pickProjectFolder(state: ExtensionState): Promise<{ projectId: string; projectPath: string } | null> {
  const current = state.getCurrentProject();
  if (current) return current;

  const folder = await vscode.window.showOpenDialog({
    canSelectFolders: true,
    canSelectFiles: false,
    canSelectMany: false,
    openLabel: 'Select Project Folder'
  });

  const selected = folder?.[0];
  if (!selected) return null;

  const projectPath = selected.fsPath;
  const projectId = projectIdFromPath(projectPath);
  return { projectId, projectPath };
}

export async function pickProjectFolderWithPrompt(state: ExtensionState): Promise<{ projectId: string; projectPath: string } | null> {
  const current = state.getCurrentProject();
  const options = current ? [
    { label: `Use current project (${current.projectId})`, value: current },
    { label: 'Choose another folder...', value: null as { projectId: string; projectPath: string } | null }
  ] : [];

  if (options.length > 0) {
    const picked = await vscode.window.showQuickPick(options, { placeHolder: 'Project selection' });
    if (!picked) return null;
    if (picked.value) return picked.value;
  }

  return pickProjectFolder(state);
}

export function getRuleIndexById(state: ExtensionState, ruleId: string): number {
  const config = state.getRulesConfig();
  if (!config) return -1;
  return config.rules.findIndex((rule) => rule.id === ruleId);
}
