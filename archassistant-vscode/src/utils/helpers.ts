import * as path from 'path';

export function normalizeProjectId(name: string): string {
  return name.trim().replace(/[^a-zA-Z0-9._-]/g, '_');
}

export function projectIdFromPath(projectPath: string): string {
  const trimmed = projectPath.replace(/[\\/]+$/, '');
  const parts = trimmed.split(/[\\/]/).filter(Boolean);
  const base = parts[parts.length - 1] || 'project';
  return normalizeProjectId(base);
}

export function isBlank(value: string | null | undefined): boolean {
  return !value || !value.trim();
}

export function ensureLeadingSlash(value: string): string {
  return value.startsWith('/') ? value : `/${value}`;
}

export function ruleKey(rule: {
  type: string;
  constraint: string;
  from_package: string;
  to_package?: string | null;
  to_packages?: string[] | null;
  pattern?: string | null;
  annotation?: string | null;
  from_selector_mode?: string | null;
  to_selector_mode?: string | null;
  from_class_type?: string | null;
  to_class_type?: string | null;
  from_layer_type?: string | null;
  to_layer_type?: string | null;
}): string {
  return [
    rule.type,
    rule.constraint,
    rule.from_package,
    rule.to_package ?? '',
    rule.to_packages?.join(',') ?? '',
    rule.pattern ?? '',
    rule.annotation ?? '',
    rule.from_selector_mode ?? '',
    rule.to_selector_mode ?? '',
    rule.from_class_type ?? '',
    rule.to_class_type ?? '',
    rule.from_layer_type ?? '',
    rule.to_layer_type ?? ''
  ].join('|');
}

export function basenameWithoutTrailingSlash(projectPath: string): string {
  const trimmed = projectPath.replace(/[\\/]+$/, '');
  return path.basename(trimmed);
}