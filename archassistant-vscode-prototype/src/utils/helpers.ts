import * as path from 'path';

export function normalizeProjectId(name: string): string {
  return name.trim().replace(/[^a-zA-Z0-9._-]/g, '_');
}

export function projectIdFromPath(projectPath: string): string {
  const base = path.basename(projectPath.replace(/[\\/]+$/, ''));
  return normalizeProjectId(base || 'project');
}

export function isBlank(value: string | null | undefined): boolean {
  return !value || !value.trim();
}

export function ensureLeadingSlash(value: string): string {
  return value.startsWith('/') ? value : `/${value}`;
}
