import * as vscode from 'vscode';

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

export function formatScore(score: number | null): string {
  if (score === null) return 'N/A';
  return `${score.toFixed(0)}%`;
}

export function truncateString(str: string, maxLength: number): string {
  if (str.length <= maxLength) return str;
  return `${str.substring(0, maxLength - 3)}...`;
}

export function getWorkspaceFolderName(): string | null {
  return vscode.workspace.workspaceFolders?.[0]?.name || null;
}