import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs/promises';
import { GeneratedFile } from '../backend/types';
import { toBackendProjectPath } from '../utils/projectPaths';

export interface SavedGeneratedFile {
  file: GeneratedFile;
  uri?: vscode.Uri;
  success: boolean;
  error?: string;
}

export interface GeneratedFilesSyncResponse {
  success: boolean;
  projectId: string;
  projectPath?: string;
  syncedFiles: number;
  compiledSources: number;
  overlaySourceDir?: string;
  overlayClassesDir?: string;
  contextRefreshed?: boolean;
  warnings?: string[];
  error?: string;
}

export interface GeneratedFilesOverlayClearResponse {
  success: boolean;
  projectId: string;
  contextRefreshed?: boolean;
  error?: string;
}

export class CodeSaver {
  async saveToProject(file: GeneratedFile, projectPath: string): Promise<vscode.Uri> {
    const isKotlin = this.detectKotlin(file.code);
    const sourceRoot = isKotlin ? 'kotlin' : 'java';
    const extension = isKotlin ? 'kt' : 'java';
    const packagePath = file.packageName ? file.packageName.replace(/\./g, path.sep) : '';
    const simpleClassName = file.className.includes('.') ? file.className.substring(file.className.lastIndexOf('.') + 1) : file.className;

    const filePath = path.join(
      projectPath,
      'src',
      'main',
      sourceRoot,
      packagePath,
      `${simpleClassName}.${extension}`
    );

    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, file.code, 'utf-8');
    return vscode.Uri.file(filePath);
  }

  async saveMultipleFiles(files: GeneratedFile[], projectPath: string): Promise<SavedGeneratedFile[]> {
    const results: SavedGeneratedFile[] = [];
    for (const file of files) {
      try {
        const uri = await this.saveToProject(file, projectPath);
        results.push({ file, uri, success: true });
      } catch (error: any) {
        results.push({
          file,
          success: false,
          error: error?.message || 'Failed to save file'
        });
      }
    }
    return results;
  }

  async saveMultipleFilesAndSync(
    files: GeneratedFile[],
    projectPath: string,
    projectId: string,
    backendBaseUrl: string
  ): Promise<{ saved: SavedGeneratedFile[]; sync?: GeneratedFilesSyncResponse }> {
    const saved = await this.saveMultipleFiles(files, projectPath);
    const successfulFiles = saved.filter((item) => item.success).map((item) => item.file);

    if (successfulFiles.length === 0) {
      return { saved };
    }

    const sync = await this.syncGeneratedFiles(
      successfulFiles,
      projectId,
      projectPath,
      backendBaseUrl
    );
    return { saved, sync };
  }

  async syncGeneratedFiles(
    files: GeneratedFile[],
    projectId: string,
    projectPath: string,
    backendBaseUrl: string
  ): Promise<GeneratedFilesSyncResponse> {
    const endpoint = `${backendBaseUrl.replace(/\/$/, '')}/api/generated-files/${encodeURIComponent(projectId)}/sync`;
    const backendProjectPath = toBackendProjectPath(projectPath);

    const payload = {
      projectPath: backendProjectPath,
      files: files.map((file) => ({
        packageName: file.packageName ?? null,
        className: file.className.includes('.')
          ? file.className.substring(file.className.lastIndexOf('.') + 1)
          : file.className,
        code: file.code,
        language: this.detectKotlin(file.code) ? 'kotlin' : 'java'
      }))
    };

    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const body = await this.safeReadJson(response);

    if (!response.ok) {
      throw new Error(body?.error || `Generated-file sync failed with HTTP ${response.status}`);
    }

    return body as GeneratedFilesSyncResponse;
  }

  async clearBackendOverlay(
    projectId: string,
    backendBaseUrl: string
  ): Promise<GeneratedFilesOverlayClearResponse> {
    const endpoint = `${backendBaseUrl.replace(/\/$/, '')}/api/generated-files/${encodeURIComponent(projectId)}`;
    const response = await fetch(endpoint, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json'
      }
    });

    const body = await this.safeReadJson(response);

    if (!response.ok) {
      throw new Error(body?.error || `Overlay clear failed with HTTP ${response.status}`);
    }

    return body as GeneratedFilesOverlayClearResponse;
  }

  async openGeneratedFile(file: GeneratedFile): Promise<void> {
    const document = await vscode.workspace.openTextDocument({
      content: file.code,
      language: this.detectKotlin(file.code) ? 'kotlin' : 'java'
    });
    await vscode.window.showTextDocument(document, {
      preview: false,
      viewColumn: vscode.ViewColumn.One
    });
  }

  private detectKotlin(code: string): boolean {
    return /\bfun\s+\w+|\bval\s+\w+|\bvar\s+\w+|\bdata\s+class\b|\bobject\s+\w+/.test(code);
  }

  private async safeReadJson(response: Response): Promise<any> {
    const text = await response.text();
    if (!text.trim()) {
      return null;
    }

    try {
      return JSON.parse(text);
    } catch {
      return { error: text };
    }
  }
}