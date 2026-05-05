import * as vscode from 'vscode';
import { BackendClient } from '../backend/BackendClient';
import { ProjectRegistry } from '../state/projectRegistry';
import { ExportFormat } from '../backend/types';

export async function exportMetricsCommand(
  backendClient: BackendClient,
  projectRegistry: ProjectRegistry
): Promise<void> {
  const project = projectRegistry.getCurrentProject();
  if (!project) {
    vscode.window.showWarningMessage('No project configured.');
    return;
  }

  const formatPick = await vscode.window.showQuickPick(
    [
      { label: 'CSV', value: ExportFormat.CSV },
      { label: 'JSON', value: ExportFormat.JSON },
      { label: 'JSON (Pretty)', value: ExportFormat.JSON_PRETTY }
    ],
    { placeHolder: 'Select export format' }
  );

  if (!formatPick) return;

  const violationsPick = await vscode.window.showQuickPick(
    [
      { label: 'Include violations', value: true },
      { label: 'Exclude violations', value: false }
    ],
    { placeHolder: 'Include violations in export?' }
  );

  if (!violationsPick) return;

  const datePick = await vscode.window.showQuickPick(
    [
      { label: 'Export all records', value: false },
      { label: 'Filter by date range', value: true }
    ],
    { placeHolder: 'Apply date filter?' }
  );

  if (!datePick) return;

  let fromDate: string | undefined;
  let toDate: string | undefined;

  if (datePick.value) {
    const fromDateInput = await vscode.window.showInputBox({
      prompt: 'From date (ISO 8601, e.g., 2026-05-02T00:00:00)',
      placeHolder: '2026-05-02T00:00:00'
    });
    if (fromDateInput === undefined) return;
    fromDate = fromDateInput || undefined;

    const toDateInput = await vscode.window.showInputBox({
      prompt: 'To date (ISO 8601, e.g., 2026-05-03T00:00:00)',
      placeHolder: '2026-05-03T00:00:00'
    });
    if (toDateInput === undefined) return;
    toDate = toDateInput || undefined;
  }

  const buffer = await backendClient.exportMetrics({
    projectId: project.projectId,
    format: formatPick.value,
    includeViolations: violationsPick.value,
    fromDate,
    toDate
  });

  const extension = formatPick.value === ExportFormat.CSV ? 'csv' : 'json';
  const defaultUri = vscode.workspace.workspaceFolders?.[0]
    ? vscode.Uri.joinPath(
        vscode.workspace.workspaceFolders[0].uri,
        `archassistant-metrics-${project.projectId}.${extension}`
      )
    : undefined;

  const saveUri = await vscode.window.showSaveDialog({
    defaultUri,
    filters: {
      [extension.toUpperCase()]: [extension]
    }
  });

  if (!saveUri) return;

  await vscode.workspace.fs.writeFile(saveUri, buffer);
  vscode.window.showInformationMessage(`Metrics exported to ${saveUri.fsPath}`);
}