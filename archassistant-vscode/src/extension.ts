import * as vscode from 'vscode';
import { BackendClient } from './backend/BackendClient';
import { StorageManager } from './state/storage';
import { ProjectRegistry } from './state/projectRegistry';
import { ArchAssistantSidebarProvider } from './ui/sidebar/ArchAssistantSidebarProvider';
import { RulesTreeDataProvider } from './ui/sidebar/RulesTreeDataProvider';
import { configureProjectCommand } from './commands/configureProject';
import { editRuleCommand, rescanRulesCommand, toggleRuleCommand } from './commands/manageRules';
import { createOutputChannel, logError, logInfo } from './utils/logger';
import { generateCodeCommand } from './commands/generateCode';
import { showMetricsCommand } from './commands/showMetrics';
import { exportMetricsCommand } from './commands/exportMetrics';

let backendClient: BackendClient;
let storageManager: StorageManager;
let projectRegistry: ProjectRegistry;
let sidebarProvider: ArchAssistantSidebarProvider;
let rulesProvider: RulesTreeDataProvider;
let outputChannel: vscode.OutputChannel;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  outputChannel = createOutputChannel('ArchAssistant');
  logInfo('ArchAssistant extension activated');

  storageManager = StorageManager.getInstance(context);
  projectRegistry = new ProjectRegistry(storageManager);

  const backendUrl = storageManager.getBackendUrl();
  backendClient = new BackendClient(backendUrl);
  logInfo(`Backend URL: ${backendUrl}`);

  sidebarProvider = new ArchAssistantSidebarProvider(backendClient, projectRegistry, storageManager);
  rulesProvider = new RulesTreeDataProvider(backendClient, projectRegistry);

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('archassistant.projectInfo', sidebarProvider),
    vscode.window.registerTreeDataProvider('archassistant.rulesList', rulesProvider)
  );

  context.subscriptions.push(
  vscode.commands.registerCommand('archassistant.generateCode', () =>
    generateCodeCommand(backendClient, projectRegistry, storageManager)
  )
  );

  context.subscriptions.push(
  vscode.commands.registerCommand('archassistant.showMetrics', () =>
    showMetricsCommand(backendClient, projectRegistry)
  ),
  vscode.commands.registerCommand('archassistant.exportMetrics', () =>
    exportMetricsCommand(backendClient, projectRegistry)
  )
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('archassistant.configureProject', () =>
      configureProjectCommand(backendClient, projectRegistry, rulesProvider, sidebarProvider)
    ),
    vscode.commands.registerCommand('archassistant.rescanRules', () =>
      rescanRulesCommand(backendClient, projectRegistry, rulesProvider)
    ),
    vscode.commands.registerCommand('archassistant.editRule', (rule: any) =>
      editRuleCommand(rule, backendClient, projectRegistry, rulesProvider)
    ),
    vscode.commands.registerCommand('archassistant.toggleRule', (rule: any) =>
      toggleRuleCommand(rule, backendClient, projectRegistry, rulesProvider)
    )
  );

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(async e => {
      if (!e.affectsConfiguration('archassistant.backendUrl')) return;

      const newUrl = vscode.workspace.getConfiguration('archassistant').get<string>(
        'backendUrl',
        'http://localhost:8080'
      );

      backendClient.updateBaseUrl(newUrl);
      await storageManager.setBackendUrl(newUrl);
      logInfo(`Backend URL updated: ${newUrl}`);
    })
  );

  try {
    const health = await backendClient.checkHealth();
    logInfo(`Backend health: ${health.status}`);
    sidebarProvider.setBackendConnected(true);
  } catch (error: any) {
    logError(`Backend connection failed: ${error.message}`);
    sidebarProvider.setBackendConnected(false);
  }

  logInfo('ArchAssistant extension fully initialized');
}

export function deactivate(): void {
  logInfo('ArchAssistant extension deactivated');
  outputChannel?.dispose();
}