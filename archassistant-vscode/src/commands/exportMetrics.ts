import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { ExportFormat } from '../backend/types';
import { logError, logInfo } from '../utils/logger';

interface PickItem<T> extends vscode.QuickPickItem {
  value: T;
}

export async function exportMetricsCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry
): Promise<void> {
  try {
    const project = await projectRegistry.getCurrentProject();
    if (!project) {
      vscode.window.showWarningMessage('No project configured.');
      return;
    }

    const format = await vscode.window.showQuickPick<PickItem<ExportFormat>>(
      [
        { label: 'CSV', value: ExportFormat.CSV },
        { label: 'JSON', value: ExportFormat.JSON },
        { label: 'JSON (Pretty)', value: ExportFormat.JSON_PRETTY }
      ],
      { placeHolder: 'Select export format' }
    );

    if (!format) return;

    const includeViolations = await vscode.window.showQuickPick<PickItem<boolean>>(
      [
        { label: 'Include violations', value: true },
        { label: 'Exclude violations', value: false }
      ],
      { placeHolder: 'Include violations in export?' }
    );

    if (!includeViolations) return;

    const useDateFilter = await vscode.window.showQuickPick<PickItem<boolean>>(
      [
        { label: 'Export all records', value: false },
        { label: 'Filter by date range', value: true }
      ],
      { placeHolder: 'Apply date filter?' }
    );

    if (!useDateFilter) return;

    let fromDate: string | undefined;
    let toDate: string | undefined;

    if (useDateFilter.value) {
      const fromDateInput = await vscode.window.showInputBox({
        prompt: 'From date (ISO 8601, e.g. 2026-05-02T00:00:00)',
        placeHolder: '2026-05-02T00:00:00'
      });
      if (fromDateInput === undefined) return;
      fromDate = fromDateInput || undefined;

      const toDateInput = await vscode.window.showInputBox({
        prompt: 'To date (ISO 8601, e.g. 2026-05-03T00:00:00)',
        placeHolder: '2026-05-03T00:00:00'
      });
      if (toDateInput === undefined) return;
      toDate = toDateInput || undefined;
    }

    await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: 'Exporting metrics...',
        cancellable: false
      },
      async () => {
        const data = await backendClient.exportMetrics({
          projectId: project.projectId,
          format: format.value,
          includeViolations: includeViolations.value,
          fromDate,
          toDate
        });

        const extension = format.value === ExportFormat.CSV ? 'csv' : 'json';
        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri;
        const defaultUri = workspaceRoot
          ? vscode.Uri.joinPath(workspaceRoot, `archassistant-metrics-${project.projectId}.${extension}`)
          : vscode.Uri.file(`archassistant-metrics-${project.projectId}.${extension}`);

        const saveUri = await vscode.window.showSaveDialog({
          defaultUri,
          filters: {
            [extension.toUpperCase()]: [extension]
          }
        });

        if (!saveUri) return;

        await vscode.workspace.fs.writeFile(saveUri, data);
        vscode.window.showInformationMessage(`Metrics exported to ${saveUri.fsPath}`);
        logInfo(`Metrics exported to ${saveUri.fsPath}`);
      }
    );
  } catch (error: any) {
    logError(`Export metrics failed: ${error.message}`);
    vscode.window.showErrorMessage(`Failed to export metrics: ${error.message}`);
  }
}