import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { logError } from '../../utils/logger';

export class MetricsPanel {
  private static currentPanel: MetricsPanel | undefined;
  private readonly panel: vscode.WebviewPanel;

  private constructor(
    panel: vscode.WebviewPanel,
    private readonly backendClient: BackendClient,
    private readonly projectRegistry: ProjectRegistry
  ) {
    this.panel = panel;
    this.panel.onDidDispose(() => this.dispose(), null, []);
    this.panel.webview.onDidReceiveMessage(
      (message) => this.handleMessage(message),
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
      MetricsPanel.currentPanel.loadMetrics();
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
    this.loadMetrics();
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

      const [metrics, comparison] = await Promise.all([
        this.backendClient.getProjectMetrics(project.projectId),
        this.backendClient.compareStrategies(project.projectId)
      ]);

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
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 20px; }
    h2, h3 { margin-top: 0; }
    .section {
      margin-bottom: 24px;
      padding: 16px;
      background: var(--vscode-textBlockBackground);
      border-radius: 6px;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-top: 12px;
    }
    .metric-card {
      padding: 14px;
      background: var(--vscode-badge-background);
      border-radius: 6px;
      text-align: center;
    }
    .metric-value { font-size: 1.4rem; font-weight: 700; }
    .metric-label { font-size: 0.85rem; opacity: 0.8; margin-top: 4px; }
    .strategy-table { width: 100%; border-collapse: collapse; margin-top: 12px; }
    .strategy-table th, .strategy-table td {
      padding: 10px;
      text-align: left;
      border-bottom: 1px solid var(--vscode-widget-border);
    }
    .strategy-table th { background: var(--vscode-badge-background); }
    .recommendation {
      padding: 14px;
      background: var(--vscode-editor-selectionBackground);
      border-radius: 6px;
      margin-top: 12px;
    }
    .recommendation-title { font-weight: 600; margin-bottom: 8px; }
    .history-list { list-style: none; padding: 0; margin-top: 12px; }
    .history-item {
      padding: 10px;
      margin: 6px 0;
      background: var(--vscode-list-hoverBackground);
      border-radius: 4px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .history-strategy { font-weight: 600; }
    .history-score.high { color: var(--vscode-terminal-ansiGreen); }
    .history-score.medium { color: var(--vscode-terminal-ansiYellow); }
    .history-score.low { color: var(--vscode-terminal-ansiRed); }
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
    .actions { margin-top: 16px; }
    .loading { text-align: center; padding: 20px; opacity: 0.7; }
    .error {
      padding: 12px;
      border-radius: 6px;
      background: var(--vscode-inputValidation-errorBackground);
      color: var(--vscode-inputValidation-errorForeground);
    }
  </style>
</head>
<body>
  <h2>📊 Project Metrics</h2>

  <div id="loading" class="loading">Loading metrics...</div>

  <div id="content" style="display: none;">
    <div class="section">
      <h3>Overview</h3>
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-value" id="totalGenerations">-</div>
          <div class="metric-label">Total Generations</div>
        </div>
        <div class="metric-card">
          <div class="metric-value" id="avgScore">-</div>
          <div class="metric-label">Average Score</div>
        </div>
        <div class="metric-card">
          <div class="metric-value" id="lastGeneration">-</div>
          <div class="metric-label">Last Generation</div>
        </div>
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
            <th>Avg Time</th>
          </tr>
        </thead>
        <tbody id="strategyTableBody"></tbody>
      </table>
      <div id="recommendation" class="recommendation" style="display: none;">
        <div class="recommendation-title">🏆 Recommended Strategy</div>
        <div id="recommendationText"></div>
      </div>
    </div>

    <div class="section">
      <h3>Recent History (Last 10)</h3>
      <ul class="history-list" id="historyList"></ul>
    </div>

    <div class="actions">
      <button onclick="refreshMetrics()">🔄 Refresh</button>
      <button onclick="exportMetrics()">📥 Export</button>
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

    function formatScore(score) {
      if (score === null || score === undefined) return 'N/A';
      return score.toFixed(0) + '%';
    }

    function formatDuration(ms) {
      if (ms === null || ms === undefined) return 'N/A';
      if (ms < 1000) return ms + 'ms';
      return (ms / 1000).toFixed(1) + 's';
    }

    function formatDate(isoString) {
      if (!isoString) return 'N/A';
      return new Date(isoString).toLocaleString();
    }

    function getScoreClass(score) {
      if (score === null || score === undefined) return '';
      if (score >= 70) return 'high';
      if (score >= 40) return 'medium';
      return 'low';
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
        document.getElementById('content').innerHTML =
          '<div class="error">Failed to load metrics: ' +
          (message.message || 'Unknown error') +
          '</div>';
      }
    });

    function renderMetrics() {
      document.getElementById('loading').style.display = 'none';
      document.getElementById('content').style.display = 'block';

      document.getElementById('totalGenerations').textContent = currentMetrics.totalGenerations || 0;
      document.getElementById('avgScore').textContent = formatScore(currentMetrics.avgScore);
      document.getElementById('lastGeneration').textContent = formatDate(currentMetrics.lastGeneration);

      const tbody = document.getElementById('strategyTableBody');
      tbody.innerHTML = '';

      if (currentComparison?.strategies) {
        Object.entries(currentComparison.strategies).forEach(([strategy, data]) => {
          const row = document.createElement('tr');
          row.innerHTML = 
            <td><strong>\${strategy}</strong></td>
            <td>\${data.totalGenerations || 0}</td>
            <td>\${((data.successRate || 0) * 100).toFixed(0)}%</td>
            <td>\${formatScore(data.avgScore)}</td>
            <td>\${(data.avgIterations || 0).toFixed(1)}</td>
            <td>\${formatDuration(data.avgGenerationTimeMs)}</td>
          ;
          tbody.appendChild(row);
        });
      }

      const recDiv = document.getElementById('recommendation');
      const recText = document.getElementById('recommendationText');

      if (currentComparison?.recommendation) {
        recDiv.style.display = 'block';
        recText.innerHTML = 
          <strong>\${currentComparison.recommendation.bestStrategy}</strong><br/>
          \${currentComparison.recommendation.reason}<br/>
          <small>Confidence: \${((currentComparison.recommendation.confidence || 0) * 100).toFixed(0)}%</small>
        ;
      } else {
        recDiv.style.display = 'none';
      }

      const historyList = document.getElementById('historyList');
      historyList.innerHTML = '';

      if (currentMetrics?.recentHistory) {
        currentMetrics.recentHistory.slice(0, 10).forEach(item => {
          const li = document.createElement('li');
          li.className = 'history-item';
          const scoreClass = getScoreClass(item.score);
          li.innerHTML = 
            <div>
              <span class="history-strategy">\${item.strategy}</span>
              <small> • \${formatDate(item.timestamp)}</small>
            </div>
            <div>
              <span class="history-score \${scoreClass}">\${formatScore(item.score)}</span>
              <small> • \${item.iterations} iter</small>
            </div>
        ;
          historyList.appendChild(li);
        });
      }
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
    }
  }

  dispose(): void {
    MetricsPanel.currentPanel = undefined;
    this.panel.dispose();
  }
}