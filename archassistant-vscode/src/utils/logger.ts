import * as vscode from 'vscode';

let defaultChannel: vscode.OutputChannel | null = null;

export class Logger {
  constructor(private readonly channel: vscode.OutputChannel) {}

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

  dispose(): void {
    this.channel.dispose();
  }
}

export function createLogger(name: string): Logger {
  defaultChannel = vscode.window.createOutputChannel(name);
  return new Logger(defaultChannel);
}

export function createOutputChannel(name: string): vscode.OutputChannel {
  defaultChannel = vscode.window.createOutputChannel(name);
  return defaultChannel;
}

export function logInfo(message: string): void {
  defaultChannel?.appendLine(`[INFO] ${timestamp()} - ${message}`);
}

export function logWarn(message: string): void {
  defaultChannel?.appendLine(`[WARN] ${timestamp()} - ${message}`);
}

export function logError(message: string): void {
  defaultChannel?.appendLine(`[ERROR] ${timestamp()} - ${message}`);
}

function timestamp(): string {
  return new Date().toISOString();
}

function format(message: string, args: unknown[]): string {
  let result = message;
  for (const arg of args) {
    result = result.replace(/\{\}/, String(arg));
  }
  return result;
}