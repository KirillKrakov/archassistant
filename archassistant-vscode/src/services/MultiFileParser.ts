import { ArtifactKind, GeneratedFile } from '../backend/types';

type TopLevelType = {
  name: string;
  kind: ArtifactKind;
  start: number;
};

export class MultiFileParser {
  parse(code: string): GeneratedFile[] {
    const normalized = code.trim();
    if (!normalized) return [];

    const blocks = this.splitByPackage(normalized);
    const files: GeneratedFile[] = [];

    for (const block of blocks) {
      const packageName = this.extractPackage(block);
      const types = this.extractTopLevelTypes(block);

      if (types.length === 0) {
        files.push({
          packageName,
          className: this.extractClassName(block) || 'Generated',
          code: block.trim(),
          artifactKind: this.detectArtifactKind(block)
        });
        continue;
      }

      const header = this.extractHeader(block, types[0].start);

      for (let i = 0; i < types.length; i++) {
        const current = types[i];
        const nextStart = i + 1 < types.length ? types[i + 1].start : block.length;
        const body = block.slice(current.start, nextStart).trim();

        files.push({
          packageName,
          className: current.name,
          code: header ? `${header}\n\n${body}` : body,
          artifactKind: current.kind
        });
      }
    }

    return files;
  }

  private splitByPackage(code: string): string[] {
    const matches = [...code.matchAll(/^package\s+[\w.]+\s*;/gm)];
    if (matches.length <= 1) return [code];

    const blocks: string[] = [];
    for (let i = 0; i < matches.length; i++) {
      const start = matches[i].index ?? 0;
      const end = i + 1 < matches.length ? (matches[i + 1].index ?? code.length) : code.length;
      blocks.push(code.slice(start, end).trim());
    }
    return blocks.filter(Boolean);
  }

  private extractPackage(block: string): string {
    const match = block.match(/package\s+([\w.]+)\s*;/);
    return match ? match[1] : '';
  }

  private extractHeader(block: string, firstTypeStart: number): string {
    return block.slice(0, firstTypeStart).trim();
  }

  private extractTopLevelTypes(block: string): TopLevelType[] {
    const pattern =
      /^\s*(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|static\s+|sealed\s+|non-sealed\s+)*(?:data\s+)?(class|interface|record|enum|object)\s+([A-Za-z_][A-Za-z0-9_]*)/gm;

    const matches: TopLevelType[] = [];
    let match: RegExpExecArray | null;

    while ((match = pattern.exec(block)) !== null) {
      matches.push({
        name: match[2],
        kind: this.mapKind(match[1]),
        start: match.index ?? 0
      });
    }

    return matches;
  }

  private mapKind(kind: string): ArtifactKind {
    switch (kind.toLowerCase()) {
      case 'interface':
        return ArtifactKind.INTERFACE;
      case 'record':
        return ArtifactKind.RECORD;
      case 'enum':
        return ArtifactKind.ENUM;
      default:
        return ArtifactKind.CLASS;
    }
  }

  private extractClassName(code: string): string | null {
    const match = code.match(/(?:class|interface|record|enum|object)\s+([A-Za-z_][A-Za-z0-9_]*)/);
    return match ? match[1] : null;
  }

  private detectArtifactKind(code: string): ArtifactKind {
    if (/\binterface\b/.test(code)) return ArtifactKind.INTERFACE;
    if (/\brecord\b/.test(code)) return ArtifactKind.RECORD;
    if (/\benum\b/.test(code)) return ArtifactKind.ENUM;
    return ArtifactKind.CLASS;
  }
}