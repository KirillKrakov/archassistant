import * as vscode from 'vscode';
import { GeneratedFile } from '../backend/types';

export interface SavedGeneratedFile {
  file: GeneratedFile;
  uri?: vscode.Uri;
  success: boolean;
  error?: string;
}

export class CodeSaver {
  async saveToFile(
    file: GeneratedFile,
    workspaceFolder: vscode.WorkspaceFolder
  ): Promise<vscode.Uri> {
    const isKotlin = this.detectKotlin(file.code);
    const sourceRoot = isKotlin ? 'kotlin' : 'java';
    const extension = isKotlin ? 'kt' : 'java';

    const packageSegments = file.packageName
      ? file.packageName.split('.').filter(Boolean)
      : [];

    const dirUri = vscode.Uri.joinPath(
      workspaceFolder.uri,
      'src',
      'main',
      sourceRoot,
      ...packageSegments
    );

    await vscode.workspace.fs.createDirectory(dirUri);

    const fileUri = vscode.Uri.joinPath(
      dirUri,
      `${file.className}.${extension}`
    );

    await vscode.workspace.fs.writeFile(fileUri, new TextEncoder().encode(file.code));
    return fileUri;
  }

  async saveMultipleFiles(
    files: GeneratedFile[],
    workspaceFolder: vscode.WorkspaceFolder
  ): Promise<SavedGeneratedFile[]> {
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