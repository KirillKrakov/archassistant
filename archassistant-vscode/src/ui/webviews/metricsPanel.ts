import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { logError } from '../../utils/logger';

export class MetricsPanel {
  private static currentPanel: MetricsPanel | undefined;
  private readonly panel: vscode.WebviewPanel;
  private readonly backendClient: BackendClient;
  private readonly projectRegistry: ProjectRegistry;

  private constructor(
    panel: vscode.WebviewPanel,
    backendClient: BackendClient,
    projectRegistry: ProjectRegistry
  ) {
    this.panel = panel;
    this.backendClient = backendClient;
    this.projectRegistry = projectRegistry;

    this.panel.onDidDispose(() => this.dispose(), null, []);
    this.panel.webview.onDidReceiveMessage(
      message => this.handleMessage(message),
      null,
      []
    );
  }

  static createOrShow(
    backendClient: BackendClient,
    projectRegistry: ProjectRegistry
  ): MetricsPanel {
    const column = vscode.window.activeTextEditor?.viewColumn;

    if (MetricsPanel.currentPanel) {
      MetricsPanel.currentPanel.panel.reveal(column);
      void MetricsPanel.currentPanel.loadMetrics();
      return MetricsPanel.currentPanel;
    }

    const panel = vscode.window.createWebviewPanel(
      'archassistant.metrics',
      'ArchAssistant Metrics',
      column || vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true
      }
    );

    MetricsPanel.currentPanel = new MetricsPanel(panel, backendClient, projectRegistry);
    MetricsPanel.currentPanel.update();
    return MetricsPanel.currentPanel;
  }

  private update(): void {
    this.panel.title = 'ArchAssistant Metrics';
    this.panel.webview.html = this.getHtmlForWebview();
    void this.loadMetrics();
  }

  private async loadMetrics(): Promise<void> {
    try {
      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        this.panel.webview.postMessage({
          type: 'error',
          message: 'No project configured'
        });
        return;
      }

      const [metricsResult, comparisonResult] = await Promise.allSettled([
        this.backendClient.getProjectMetrics(project.projectId),
        this.backendClient.compareStrategies(project.projectId)
      ]);

      const metrics = metricsResult.status === 'fulfilled' ? metricsResult.value : null;
      const comparison = comparisonResult.status === 'fulfilled' ? comparisonResult.value : null;
      const error =
        metricsResult.status === 'rejected'
          ? metricsResult.reason?.message
          : comparisonResult.status === 'rejected'
            ? comparisonResult.reason?.message
            : null;

      if (!metrics) {
        this.panel.webview.postMessage({
          type: 'error',
          message: error || 'Failed to load metrics'
        });
        return;
      }

      this.panel.webview.postMessage({
        type: 'metrics',
        metrics,
        comparison
      });
    } catch (error: any) {
      logError(`Load metrics failed: ${error.message}`);
      this.panel.webview.postMessage({
        type: 'error',
        message: error.message
      });
    }
  }

  private getHtmlForWebview(): string {
    return `
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<style>
body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 18px; }
h2, h3 { margin: 0 0 10px 0; }
.section { margin-bottom: 18px; padding: 12px; background: var(--vscode-textBlockBackground); border-radius: 6px; }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.metric-card { padding: 10px; background: var(--vscode-badge-background); border-radius: 6px; text-align: center; }
.metric-value { font-size: 1rem; font-weight: 700; }
.metric-label { font-size: 0.8rem; opacity: 0.8; }
.strategy-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
.strategy-table th, .strategy-table td { padding: 8px; text-align: left; border-bottom: 1px solid var(--vscode-widget-border); }
.strategy-table th { background: var(--vscode-badge-background); }
.recommendation {
  padding: 10px;
  background: var(--vscode-editor-selectionBackground);
  border-radius: 6px;
  margin-top: 10px;
}
.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-item {
  padding: 10px;
  background: var(--vscode-list-hoverBackground);
  border-radius: 6px;
}

.history-item details {
  width: 100%;
}

.history-item summary {
  cursor: pointer;
  list-style: none;
}

.history-item summary::-webkit-details-marker {
  display: none;
}

.history-summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.history-summary-left,
.history-summary-right {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-details {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-details pre {
  margin: 6px 0 0;
  padding: 10px;
  border-radius: 4px;
  background: var(--vscode-editor-background);
  white-space: pre-wrap;
  overflow: auto;
}

.history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.history-pill {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--vscode-badge-background);
  font-size: 0.8rem;
}
button {
  padding: 8px 14px;
  border: none;
  border-radius: 4px;
  background: var(--vscode-button-background);
  color: var(--vscode-button-foreground);
  cursor: pointer;
  font-weight: 600;
  margin-right: 8px;
}
button:hover { background: var(--vscode-button-hoverBackground); }
.loading { padding: 18px; opacity: 0.8; }
.error { color: var(--vscode-problemsErrorIcon); white-space: pre-wrap; }
.small { font-size: 0.9rem; opacity: 0.85; }
</style>
</head>
<body>
  <h2>Project Metrics</h2>
  <div id="loading" class="loading">Loading metrics...</div>

  <div id="content" style="display:none;">
    <div class="section">
      <h3>Overview</h3>
      <div class="metrics-grid">
        <div class="metric-card"><div class="metric-value" id="totalGenerations">-</div><div class="metric-label">Total Generations</div></div>
        <div class="metric-card"><div class="metric-value" id="avgScore">-</div><div class="metric-label">Average Score</div></div>
        <div class="metric-card"><div class="metric-value" id="successRate">-</div><div class="metric-label">Success Rate</div></div>
        <div class="metric-card"><div class="metric-value" id="lastGeneration">-</div><div class="metric-label">Last Generation</div></div>
        <div class="metric-card"><div class="metric-value" id="avgIterations">-</div><div class="metric-label">Avg Iterations</div></div>
        <div class="metric-card"><div class="metric-value" id="avgTotalTimeMs">-</div><div class="metric-label">Avg Total Time</div></div>
      </div>
    </div>

    <div class="section">
      <h3>Strategy Comparison</h3>
      <table class="strategy-table">
        <thead>
          <tr>
            <th>Strategy</th>
            <th>Generations</th>
            <th>Success Rate</th>
            <th>Avg Score</th>
            <th>Avg Iterations</th>
            <th>Avg Generation</th>
            <th>Avg Validation</th>
            <th>Avg Total Time</th>
          </tr>
        </thead>
        <tbody id="strategyTableBody"></tbody>
      </table>
      <div id="recommendation" class="recommendation" style="display:none;"></div>
    </div>

    <div class="section">
      <h3>Recent History</h3>
      <ul class="history-list" id="historyList"></ul>
    </div>

    <div class="section">
      <button onclick="refreshMetrics()">Refresh</button>
      <button onclick="exportMetrics()">Export</button>
      <button onclick="clearHistory()">Clear</button>
    </div>
  </div>

<script>
const vscode = acquireVsCodeApi();
let currentMetrics = null;
let currentComparison = null;

function refreshMetrics() {
  vscode.postMessage({ command: 'refresh' });
}

function exportMetrics() {
  vscode.postMessage({ command: 'export' });
}

function fmtScore(score) {
  return score === null || score === undefined ? 'N/A' : Number(score).toFixed(0) + '%';
}

function fmtTime(ms) {
  return ms === null || ms === undefined ? 'N/A' : (ms < 1000 ? ms + 'ms' : (ms / 1000).toFixed(1) + 's');
}

function fmtPercent(value) {
  return value === null || value === undefined ? 'N/A' : Number(value).toFixed(0) + '%';
}

function clearHistory() {
  vscode.postMessage({ command: 'clearHistory' });
}

function fmtDate(isoString) {
  if (!isoString) return 'N/A';
  const date = new Date(isoString);
  return date.toLocaleString();
}

function escapeHtml(text) {
  const div = document.createElement('div');
  div.appendChild(document.createTextNode(text || ''));
  return div.innerHTML;
}

window.addEventListener('message', event => {
  const message = event.data;

  if (message.type === 'metrics') {
    currentMetrics = message.metrics;
    currentComparison = message.comparison;
    renderMetrics();
  }

  if (message.type === 'error') {
    document.getElementById('loading').style.display = 'none';
    document.getElementById('content').style.display = 'block';
    const section = document.querySelector('.section');
    if (section) {
      section.innerHTML = '<div class="error">Failed to load metrics: ' + (message.message || 'Unknown error') + '</div>';
    }
  }
});

function renderMetrics() {
  document.getElementById('loading').style.display = 'none';
  document.getElementById('content').style.display = 'block';

  const totalGenerations = currentMetrics?.totalGenerations ?? 0;
  const avgScore = currentMetrics?.avgScore;
  const lastGeneration = currentMetrics?.lastGeneration;
  const history = currentMetrics?.recentHistory || [];

  const successfulGenerations =
    currentMetrics?.successfulGenerations ??
    history.filter((item) => item.success).length;

  const successRatePercent =
    currentMetrics?.successRatePercent ??
    (currentMetrics?.successRate != null
      ? currentMetrics.successRate * 100
      : totalGenerations > 0
        ? (successfulGenerations / totalGenerations) * 100
        : null);

  const avgIterations =
    currentMetrics?.avgIterations ??
    (history.length
      ? history.reduce((s, x) => s + (x.iterations || 0), 0) / history.length
      : null);

  const avgGenerationTimeMs =
    currentMetrics?.avgGenerationTimeMs ??
    (history.length
      ? history.reduce((s, x) => s + (x.generationTimeMs || 0), 0) / history.length
      : null);

  const avgValidationTimeMs =
    currentMetrics?.avgValidationTimeMs ??
    (history.length
      ? history.reduce((s, x) => s + (x.validationTimeMs || 0), 0) / history.length
      : null);

  const avgTotalTimeMs =
    currentMetrics?.avgTotalTimeMs ??
    ((avgGenerationTimeMs != null || avgValidationTimeMs != null)
      ? (avgGenerationTimeMs || 0) + (avgValidationTimeMs || 0)
      : null);

  document.getElementById('totalGenerations').textContent = totalGenerations;
  document.getElementById('avgScore').textContent = fmtScore(avgScore);
  document.getElementById('successRate').textContent = fmtPercent(successRatePercent);
  document.getElementById('lastGeneration').textContent = fmtDate(lastGeneration);
  document.getElementById('avgIterations').textContent = avgIterations === null ? 'N/A' : avgIterations.toFixed(1);
  document.getElementById('avgTotalTimeMs').textContent = fmtTime(avgTotalTimeMs);

  const tbody = document.getElementById('strategyTableBody');
  tbody.innerHTML = '';

  if (currentComparison?.strategies) {
    Object.entries(currentComparison.strategies).forEach(([strategy, data]) => {
      const avgTotalTime = data.avgTotalTimeMs ?? ((data.avgGenerationTimeMs ?? 0) + (data.avgValidationTimeMs ?? 0));

      const row = document.createElement('tr');
      row.innerHTML = \`
        <td><strong>\${strategy}</strong></td>
        <td>\${data.totalGenerations ?? 0}</td>
        <td>\${fmtPercent((data.successRate || 0) * 100)}</td>
        <td>\${fmtScore(data.avgScore)}</td>
        <td>\${(data.avgIterations ?? 0).toFixed(1)}</td>
        <td>\${fmtTime(data.avgGenerationTimeMs)}</td>
        <td>\${fmtTime(data.avgValidationTimeMs)}</td>
        <td>\${fmtTime(avgTotalTime)}</td>
      \`;
      tbody.appendChild(row);
    });
  }

  const rec = document.getElementById('recommendation');
  if (currentComparison?.recommendation) {
    rec.style.display = 'block';
    rec.innerHTML = \`
      <strong>Recommended:</strong> \${currentComparison.recommendation.bestStrategy}<br/>
      \${currentComparison.recommendation.reason}<br/>
      Confidence: \${(currentComparison.recommendation.confidence * 100).toFixed(0)}%
    \`;
  } else {
    rec.style.display = 'none';
    rec.innerHTML = '';
  }

  const historyList = document.getElementById('historyList');
  historyList.innerHTML = '';

  history.slice(0, 10).forEach((item) => {
    const li = document.createElement('li');
    li.className = 'history-item';

    const hasPrompt = item.prompt && item.prompt.trim().length > 0;
    const hasCode = item.generatedCode && item.generatedCode.trim().length > 0;

    const summaryHtml = \`
      <span class="history-summary-left">
        <strong>\${escapeHtml(item.strategy)}</strong>
        <span class="small">\${fmtDate(item.timestamp)}</span>
      </span>
      <span class="history-summary-right">
        <span>\${fmtScore(item.score)}</span>
        <span class="small">\${item.iterations} iter</span>
      </span>
    \`;

    const metaPills = [
      item.totalTimeMs != null ? \`Total: \${fmtTime(item.totalTimeMs)}\` : null,
      item.generationTimeMs != null ? \`Gen: \${fmtTime(item.generationTimeMs)}\` : null,
      item.validationTimeMs != null ? \`Val: \${fmtTime(item.validationTimeMs)}\` : null,
      item.violationsCount != null ? \`Violations: \${item.violationsCount}\` : null
    ].filter(Boolean).map(text => \`<span class="history-pill">\${text}</span>\`).join('');

    const detailsHtml = \`
      <div class="history-details">
        <div class="history-meta">\${metaPills}</div>
        \${hasPrompt ? \`<div><strong>Prompt</strong><pre>\${escapeHtml(item.prompt)}</pre></div>\` : ''}
        \${hasCode ? \`<div><strong>Generated Code</strong><pre>\${escapeHtml(item.generatedCode)}</pre></div>\` : ''}
      </div>
    \`;

    if (hasPrompt || hasCode) {
      li.innerHTML = \`
        <details>
          <summary class="history-summary">\${summaryHtml}</summary>
          \${detailsHtml}
        </details>
      \`;
    } else {
      li.innerHTML = \`
        <div class="history-summary">\${summaryHtml}</div>
      \`;
    }

    historyList.appendChild(li);
  });
}
</script>
</body>
</html>`;
  }

  private async handleMessage(message: any): Promise<void> {
    switch (message?.command) {
      case 'refresh':
        await this.loadMetrics();
        break;
      case 'export':
        await vscode.commands.executeCommand('archassistant.exportMetrics');
        break;
      case 'clearHistory':
        await this.handleClearHistory();
        break;
    }
  }

  private async handleClearHistory(): Promise<void> {
    try {
      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        throw new Error('No project configured');
      }

      const confirmed = await vscode.window.showWarningMessage(
        `Delete all metric records for project ${project.projectId}?`,
        { modal: true },
        'Clear',
        'Cancel'
      );

      if (confirmed !== 'Clear') {
        return;
      }

      const result = await this.backendClient.clearProjectMetrics(project.projectId);
      vscode.window.showInformationMessage(
        `Deleted ${result.deletedCount} metric record(s) for ${project.projectId}`
      );

      await this.loadMetrics();
    } catch (error: any) {
      logError(`Clear history failed: ${error.message}`);
      vscode.window.showErrorMessage(`Failed to clear metrics: ${error.message}`);
    }
  }

  dispose(): void {
    MetricsPanel.currentPanel = undefined;
    this.panel.dispose();
  }
}