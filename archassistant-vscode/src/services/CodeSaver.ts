import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs/promises';
import { ArtifactKind, GeneratedFile } from '../backend/types';

export interface SavedGeneratedFile {
  file: GeneratedFile;
  uri?: vscode.Uri;
  success: boolean;
  error?: string;
}

export class CodeSaver {
  async saveToFile(file: GeneratedFile, workspaceFolder: vscode.WorkspaceFolder): Promise<vscode.Uri> {
    const isKotlin = this.detectKotlin(file.code);
    const sourceRoot = isKotlin ? 'kotlin' : 'java';
    const extension = isKotlin ? 'kt' : 'java';

    const packagePath = file.packageName ? file.packageName.replace(/\./g, path.sep) : '';
    const filePath = path.join(
      workspaceFolder.uri.fsPath,
      'src',
      'main',
      sourceRoot,
      packagePath,
      `${file.className}.${extension}`
    );

    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, file.code, 'utf-8');

    return vscode.Uri.file(filePath);
  }

  async saveMultipleFiles(files: GeneratedFile[], workspaceFolder: vscode.WorkspaceFolder): Promise<SavedGeneratedFile[]> {
    const results: SavedGeneratedFile[] = [];

    for (const file of files) {
      try {
        const uri = await this.saveToFile(file, workspaceFolder);
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
}