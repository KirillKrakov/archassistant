import * as vscode from 'vscode';
import { ArchitecturalRule } from '../../backend/types';
import { getRuleEditorHtml } from './rulesEditor';

export class RuleEditorPanel {
  private static currentPanel: RuleEditorPanel | undefined;

  private readonly panel: vscode.WebviewPanel;
  private resolveResult: ((value: ArchitecturalRule | null) => void) | undefined;
  private disposed = false;

  private constructor(
    panel: vscode.WebviewPanel,
    private readonly initialRule: ArchitecturalRule,
    private readonly title: string,
    resolve: (value: ArchitecturalRule | null) => void
  ) {
    this.panel = panel;
    this.resolveResult = resolve;

    this.panel.onDidDispose(() => this.finish(null), null, []);
    this.panel.webview.onDidReceiveMessage((message) => this.handleMessage(message), null, []);
    this.render();
  }

  static open(initialRule: ArchitecturalRule, title = 'Edit Rule'): Promise<ArchitecturalRule | null> {
    if (RuleEditorPanel.currentPanel) {
      RuleEditorPanel.currentPanel.finish(null);
    }

    const panel = vscode.window.createWebviewPanel(
      'archassistant.ruleEditor',
      title,
      vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true
      }
    );

    return new Promise((resolve) => {
      RuleEditorPanel.currentPanel = new RuleEditorPanel(panel, initialRule, title, resolve);
    });
  }

  private render(): void {
    this.panel.title = this.title;
    this.panel.webview.html = getRuleEditorHtml(this.initialRule, this.title);
  }

  private async handleMessage(message: any): Promise<void> {
    switch (message?.command) {
      case 'save': {
        const payload = message.rule ?? {};
        const merged: ArchitecturalRule = {
          ...this.initialRule,
          ...payload,
          id: this.initialRule.id,
          suggested: this.initialRule.suggested,
          weight: this.initialRule.weight,
          name: typeof payload.name === 'string' ? payload.name.trim() : this.initialRule.name,
          description:
            typeof payload.description === 'string' && payload.description.trim()
              ? payload.description.trim()
              : null,
          from_package:
            typeof payload.from_package === 'string'
              ? payload.from_package.trim()
              : this.initialRule.from_package,
          to_package:
            typeof payload.to_package === 'string' && payload.to_package.trim()
              ? payload.to_package.trim()
              : null,
          pattern:
            typeof payload.pattern === 'string' && payload.pattern.trim()
              ? payload.pattern.trim()
              : null,
          annotation:
            typeof payload.annotation === 'string' && payload.annotation.trim()
              ? payload.annotation.trim()
              : null
        };

        this.finish(merged);
        break;
      }

      case 'cancel':
        this.finish(null);
        break;
    }
  }

  private finish(result: ArchitecturalRule | null): void {
    if (this.disposed) return;
    this.disposed = true;

    if (RuleEditorPanel.currentPanel === this) {
      RuleEditorPanel.currentPanel = undefined;
    }

    const resolve = this.resolveResult;
    this.resolveResult = undefined;

    try {
      resolve?.(result);
    } finally {
      this.panel.dispose();
    }
  }
}