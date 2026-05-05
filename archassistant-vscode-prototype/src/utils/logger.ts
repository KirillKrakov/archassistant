import * as vscode from 'vscode';

export class Logger {
  private readonly channel: vscode.OutputChannel;

  constructor(name: string) {
    this.channel = vscode.window.createOutputChannel(name);
  }

  info(message: string, ...args: unknown[]): void {
    this.channel.appendLine(`[INFO] ${format(message, args)}`);
  }

  warn(message: string, ...args: unknown[]): void {
    this.channel.appendLine(`[WARN] ${format(message, args)}`);
  }

  error(message: string, ...args: unknown[]): void {
    this.channel.appendLine(`[ERROR] ${format(message, args)}`);
  }

  show(): void {
    this.channel.show(true);
  }
}

function format(message: string, args: unknown[]): string {
  let result = message;
  for (const arg of args) {
    result = result.replace(/\{\}/, String(arg));
  }
  return result;
}
