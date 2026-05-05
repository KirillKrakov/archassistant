import * as vscode from 'vscode';
import { BackendClient } from './backend/BackendClient';
import { ExtensionState } from './state/ExtensionState';
import { ProjectRegistry } from './state/projectRegistry';
import { ArchAssistantSidebarProvider } from './ui/sidebar/ArchAssistantSidebarProvider';
import { RulesTreeDataProvider } from './ui/sidebar/RulesTreeDataProvider';
import { RulesManager } from './services/RulesManager';
import { RuleEditor } from './services/RuleEditor';
import { startProjectCommand } from './commands/startProject';
import { configureProjectCommand } from './commands/configureProject';
import {
  toggleRuleCommand,
  editRuleCommand,
  deleteRuleCommand,
  addCustomRuleCommand
} from './commands/manageRules';
import { refreshRulesCommand } from './commands/refreshRules';
import { saveRulesCommand } from './commands/saveRules';
import { generateCodeCommand } from './commands/generateCode';
import { showMetricsCommand } from './commands/showMetrics';
import { exportMetricsCommand } from './commands/exportMetrics';
import { createLogger, logError, logInfo, Logger } from './utils/logger';

let backendClient: BackendClient;
let storageManager: ExtensionState;
let projectRegistry: ProjectRegistry;
let rulesManager: RulesManager;
let ruleEditor: RuleEditor;
let sidebarProvider: ArchAssistantSidebarProvider;
let rulesProvider: RulesTreeDataProvider;
let logger: Logger;
let outputChannel: vscode.OutputChannel;

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  outputChannel = vscode.window.createOutputChannel('ArchAssistant');
  logger = createLogger('ArchAssistant');

  logInfo('ArchAssistant extension activated');

  storageManager = new ExtensionState(context.workspaceState);
  projectRegistry = new ProjectRegistry(context.workspaceState);

  const backendUrl = storageManager.getBackendUrl();
  backendClient = new BackendClient(backendUrl);
  rulesManager = new RulesManager(backendClient, storageManager);
  ruleEditor = new RuleEditor();

  sidebarProvider = new ArchAssistantSidebarProvider(
    backendClient,
    projectRegistry,
    storageManager
  );
  rulesProvider = new RulesTreeDataProvider(projectRegistry, storageManager);

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('archassistant.projectInfo', sidebarProvider),
    vscode.window.registerTreeDataProvider('archassistant.rulesList', rulesProvider)
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('archassistant.startProject', () =>
      startProjectCommand(storageManager, projectRegistry, logger)
    ),
    vscode.commands.registerCommand('archassistant.configureProject', () =>
      configureProjectCommand(
        storageManager,
        projectRegistry,
        rulesManager,
        logger,
        rulesProvider
      )
    ),
    vscode.commands.registerCommand('archassistant.refreshRules', () =>
      refreshRulesCommand(storageManager, projectRegistry, rulesManager, logger, () =>
        rulesProvider.refresh()
      )
    ),
    vscode.commands.registerCommand('archassistant.saveRules', () =>
      saveRulesCommand(storageManager, projectRegistry, rulesManager, logger)
    ),
    vscode.commands.registerCommand('archassistant.toggleRule', (ruleId: string) =>
      toggleRuleCommand(ruleId, storageManager, rulesManager, () => rulesProvider.refresh())
    ),
    vscode.commands.registerCommand('archassistant.editRule', (ruleId: string) =>
      editRuleCommand(
        ruleId,
        storageManager,
        rulesManager,
        ruleEditor,
        () => rulesProvider.refresh()
      )
    ),
    vscode.commands.registerCommand('archassistant.deleteRule', (ruleId: string) =>
      deleteRuleCommand(ruleId, storageManager, rulesManager, () =>
        rulesProvider.refresh()
      )
    ),
    vscode.commands.registerCommand(
      'archassistant.addCustomRule',
      (rule?: any) =>
        addCustomRuleCommand(
          storageManager,
          rulesManager,
          ruleEditor,
          () => rulesProvider.refresh(),
          rule
        )
    ),
    vscode.commands.registerCommand('archassistant.generateCode', () =>
      generateCodeCommand(backendClient, projectRegistry, storageManager)
    ),
    vscode.commands.registerCommand('archassistant.showMetrics', () =>
      showMetricsCommand(backendClient, projectRegistry)
    ),
    vscode.commands.registerCommand('archassistant.exportMetrics', () =>
      exportMetricsCommand(backendClient, projectRegistry)
    )
  );

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(async (e) => {
      if (e.affectsConfiguration('archassistant.backendUrl')) {
        const newUrl = vscode.workspace
          .getConfiguration('archassistant')
          .get('backendUrl', 'http://localhost:8080');

        backendClient.updateBaseUrl(newUrl);
        await storageManager.setBackendUrl(newUrl);
        logInfo(`Backend URL updated: ${newUrl}`);
      }
    })
  );

  try {
    const health = await backendClient.health();
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
  logger?.dispose?.();
}