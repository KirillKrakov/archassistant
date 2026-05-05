import * as vscode from 'vscode';

export class Storage<T> {
  constructor(private readonly key: string, private readonly memento: vscode.Memento) {}

  get(defaultValue: T): T {
    return (this.memento.get<T>(this.key) ?? defaultValue);
  }

  async set(value: T): Promise<void> {
    await this.memento.update(this.key, value);
  }

  async clear(): Promise<void> {
    await this.memento.update(this.key, undefined);
  }
}
