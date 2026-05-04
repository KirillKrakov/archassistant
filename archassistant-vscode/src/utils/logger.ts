import * as vscode from 'vscode';

let outputChannel: vscode.OutputChannel | null = null;

export function createOutputChannel(name: string): vscode.OutputChannel {
  outputChannel = vscode.window.createOutputChannel(name);
  return outputChannel;
}

export function logInfo(message: string): void {
  if (!outputChannel) return;
  outputChannel.appendLine(`[INFO] ${new Date().toISOString()} - ${message}`);
}

export function logError(message: string): void {
  if (!outputChannel) return;
  outputChannel.appendLine(`[ERROR] ${new Date().toISOString()} - ${message}`);
}

export function logWarn(message: string): void {
  if (!outputChannel) return;
  outputChannel.appendLine(`[WARN] ${new Date().toISOString()} - ${message}`);
}

export function showOutputChannel(): void {
  outputChannel?.show();
}