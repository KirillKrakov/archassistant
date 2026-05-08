import * as path from 'path';
import * as vscode from 'vscode';

async function exists(uri: vscode.Uri): Promise<boolean> {
  try {
    await vscode.workspace.fs.stat(uri);
    return true;
  } catch {
    return false;
  }
}

export async function isProjectCompiled(projectPath: string): Promise<boolean> {
  const candidates = [
    ['build', 'classes', 'java', 'main'],
    ['build', 'classes', 'kotlin', 'main'],
    ['build', 'classes'],
    ['target', 'classes'],
    ['out', 'production']
  ];

  for (const parts of candidates) {
    const candidate = vscode.Uri.file(path.join(projectPath, ...parts));
    if (await exists(candidate)) {
      return true;
    }
  }

  return false;
}