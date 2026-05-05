import * as vscode from 'vscode';
import { BackendClient } from './backend/BackendClient';
import { ExtensionState } from './state/ExtensionState';
import { ProjectRegistry } from './state/projectRegistry';
import { Logger } from './utils/logger';
import { RulesTreeDataProvider } from './ui/sidebar/RulesTreeDataProvider';
import { RulesManager } from './services/RulesManager';
import { RuleEditor } from './services/RuleEditor';
import { startCommand } from './commands/start';
import { configureProjectCommand } from './commands/configureProject';
import { refreshRulesCommand } from './commands/refreshRules';
import { saveRulesCommand } from './commands/saveRules';
import { toggleRuleCommand, editRuleCommand, deleteRuleCommand, addCustomRuleCommand } from './commands/manageRules';

export function activate(context: vscode.ExtensionContext) {
  const logger = new Logger('ArchAssistant');
  const state = new ExtensionState(context.workspaceState);
  const registry = new ProjectRegistry(context.workspaceState);
  const editor = new RuleEditor();
  const provider = new RulesTreeDataProvider(state);

  const register = (command: string, fn: (...args: any[]) => unknown) => {
    context.subscriptions.push(vscode.commands.registerCommand(command, fn));
  };

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('archassistantRulesView', provider)
  );

  const backendUrl = () => vscode.workspace.getConfiguration('archassistant').get<string>('backendUrl', 'http://localhost:8080');
  const client = () => new BackendClient(backendUrl());
  const rulesManager = () => new RulesManager(client(), state);
  const refresh = () => provider.refresh();

  register('archassistant.start', async () => {
    try {
      await startCommand(context, state, registry, logger);
      refresh();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`ArchAssistant start failed: ${message}`);
    }
  });

  register('archassistant.configureProject', async () => {
    try {
      await configureProjectCommand(state, rulesManager(), logger, refresh);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Configure failed: ${message}`);
    }
  });

  register('archassistant.refreshRules', async () => {
    try {
      await refreshRulesCommand(state, rulesManager(), logger, refresh);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Refresh failed: ${message}`);
    }
  });

  register('archassistant.saveRules', async () => {
    try {
      await saveRulesCommand(state, rulesManager(), logger);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Save failed: ${message}`);
    }
  });

  register('archassistant.toggleRule', async (ruleId: string) => {
    try {
      await toggleRuleCommand(ruleId, state, rulesManager(), refresh);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Toggle failed: ${message}`);
    }
  });

  register('archassistant.editRule', async (ruleId: string) => {
    try {
      await editRuleCommand(ruleId, state, rulesManager(), editor, refresh);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Edit failed: ${message}`);
    }
  });

  register('archassistant.deleteRule', async (ruleId: string) => {
    try {
      await deleteRuleCommand(ruleId, rulesManager(), refresh);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Delete failed: ${message}`);
    }
  });

  register('archassistant.addCustomRule', async (rule?: any) => {
    try {
      await addCustomRuleCommand(state, rulesManager(), editor, refresh, rule);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      logger.error(message);
      vscode.window.showErrorMessage(`Add rule failed: ${message}`);
    }
  });

  logger.info('ArchAssistant extension activated');
}

export function deactivate() {
  // no-op
}
