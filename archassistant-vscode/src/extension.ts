import * as vscode from 'vscode';
import { BackendClient } from './backend/BackendClient';
import { DockerBackendLauncher } from './backend/DockerBackendLauncher';
import { ExtensionState } from './state/ExtensionState';
import { ProjectRegistry } from './state/projectRegistry';
import { ArchAssistantSidebarProvider } from './ui/sidebar/ArchAssistantSidebarProvider';
import { RulesTreeDataProvider } from './ui/sidebar/RulesTreeDataProvider';
import { RulesManager } from './services/RulesManager';
import { RuleEditor } from './services/RuleEditor';
import { startProjectCommand } from './commands/startProject';
import { configureProjectCommand } from './commands/configureProject';
import { editRuleCommand, deleteRuleCommand, addCustomRuleCommand } from './commands/manageRules';
import { refreshRulesCommand } from './commands/refreshRules';
import { saveRulesCommand } from './commands/saveRules';
import { getActualRulesCommand } from './commands/getActualRules';
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
let statusBarItem: vscode.StatusBarItem;
let healthTimer: NodeJS.Timeout | undefined;

async function updateBackendStatus(): Promise<void> {
  try {
    const health = await backendClient.health();
    const connected = health.status?.toUpperCase() === 'UP';
    sidebarProvider.setBackendConnected(connected);

    statusBarItem.text = connected
      ? '$(server) ArchAssistant: Connected'
      : '$(circle-slash) ArchAssistant: Disconnected';
    statusBarItem.tooltip = connected ? 'Backend is reachable' : 'Backend is not reachable';
  } catch {
    sidebarProvider.setBackendConnected(false);
    statusBarItem.text = '$(circle-slash) ArchAssistant: Disconnected';
    statusBarItem.tooltip = 'Backend is not reachable';
  }
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  logger = createLogger('ArchAssistant');
  logInfo('ArchAssistant extension activated');

  storageManager = new ExtensionState(context.workspaceState);
  projectRegistry = new ProjectRegistry(context.workspaceState);

  await storageManager.resetSessionState();

  const backendUrl = storageManager.getBackendUrl();
  backendClient = new BackendClient(backendUrl);
  rulesManager = new RulesManager(backendClient, storageManager);
  ruleEditor = new RuleEditor();

  sidebarProvider = new ArchAssistantSidebarProvider(backendClient, projectRegistry, storageManager);
  rulesProvider = new RulesTreeDataProvider(projectRegistry, storageManager);

  statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, 100);
  statusBarItem.command = 'archassistant.startProject';
  statusBarItem.show();
  context.subscriptions.push(statusBarItem);

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider('archassistant.projectInfo', sidebarProvider),
    vscode.window.registerTreeDataProvider('archassistant.rulesList', rulesProvider)
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('archassistant.startProject', () =>
      startProjectCommand(
        backendClient,
        storageManager,
        projectRegistry,
        sidebarProvider,
        rulesProvider,
        logger
      )
    ),
    vscode.commands.registerCommand('archassistant.configureProject', () =>
      configureProjectCommand(
        backendClient,
        storageManager,
        projectRegistry,
        rulesManager,
        logger,
        sidebarProvider,
        rulesProvider
      )
    ),
    vscode.commands.registerCommand('archassistant.getActualRules', () =>
      getActualRulesCommand(
        storageManager,
        projectRegistry,
        rulesManager,
        rulesProvider
      )
    ),
    vscode.commands.registerCommand('archassistant.refreshRules', () =>
      refreshRulesCommand(storageManager, projectRegistry, rulesManager, logger, rulesProvider)
    ),
    vscode.commands.registerCommand('archassistant.saveRules', () =>
      saveRulesCommand(storageManager, projectRegistry, rulesManager, logger, rulesProvider)
    ),
    vscode.commands.registerCommand('archassistant.editRule', (ruleId?: string) =>
      editRuleCommand(ruleId, storageManager, rulesManager, ruleEditor, () => rulesProvider.refresh())
    ),
    vscode.commands.registerCommand('archassistant.deleteRule', (ruleId?: string) =>
      deleteRuleCommand(ruleId, storageManager, rulesManager, () => rulesProvider.refresh())
    ),
    vscode.commands.registerCommand('archassistant.addCustomRule', (rule?: any) =>
      addCustomRuleCommand(storageManager, rulesManager, ruleEditor, () => rulesProvider.refresh(), rule)
    ),
    vscode.commands.registerCommand('archassistant.generateCode', () =>
      generateCodeCommand(backendClient, projectRegistry, storageManager)
    ),
    vscode.commands.registerCommand('archassistant.showMetrics', () =>
      showMetricsCommand(backendClient, projectRegistry)
    ),
    vscode.commands.registerCommand('archassistant.compareStrategies', () =>
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
        await updateBackendStatus();
      }
    })
  );

  await updateBackendStatus();
  healthTimer = setInterval(() => {
    void updateBackendStatus();
  }, 10000);

  context.subscriptions.push(
    new vscode.Disposable(() => {
      if (healthTimer) {
        clearInterval(healthTimer);
        healthTimer = undefined;
      }
    })
  );

  logInfo('ArchAssistant extension fully initialized');
}

export async function deactivate(): Promise<void> {
  logInfo('ArchAssistant extension deactivated');

  if (healthTimer) {
    clearInterval(healthTimer);
    healthTimer = undefined;
  }

  const launchInfo = storageManager?.getBackendLaunchInfo();
  if (launchInfo && storageManager?.isBackendStarted()) {
    try {
      const launcher = new DockerBackendLauncher();
      await launcher.stop({
        projectPath: launchInfo.projectPath,
        composeDirectory: launchInfo.composeDirectory,
        serviceName: launchInfo.serviceName,
        backendUrl: launchInfo.backendUrl
      });
      logInfo('Backend stopped on extension shutdown');
    } catch (error: any) {
      logError(`Failed to stop backend on deactivate: ${error.message}`);
    }
  }

  statusBarItem?.dispose();
  logger?.dispose?.();
}