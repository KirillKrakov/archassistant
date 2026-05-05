import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ExtensionState } from '../../state/ExtensionState';
import {
  ArtifactKind,
  CodeGenerationRequest,
  CodeGenerationResponse,
  GeneratedFile,
  StrategyType
} from '../../backend/types';
import { MultiFileParser } from '../../services/MultiFileParser';
import { CodeSaver } from '../../services/CodeSaver';
import { logError, logInfo } from '../../utils/logger';

export class GenerateCodePanel {
  private static currentPanel: GenerateCodePanel | undefined;

  private readonly panel: vscode.WebviewPanel;
  private readonly multiFileParser = new MultiFileParser();
  private readonly codeSaver = new CodeSaver();

  private constructor(
    panel: vscode.WebviewPanel,
    private readonly backendClient: BackendClient,
    private readonly projectRegistry: ProjectRegistry,
    private readonly storageManager: ExtensionState
  ) {
    this.panel = panel;
    this.panel.onDidDispose(() => this.dispose(), null, []);
    this.panel.webview.onDidReceiveMessage((message) => this.handleMessage(message), null, []);
  }

  static createOrShow(
    backendClient: BackendClient,
    projectRegistry: ProjectRegistry,
    storageManager: ExtensionState
  ): GenerateCodePanel {
    const column = vscode.window.activeTextEditor?.viewColumn;

    if (GenerateCodePanel.currentPanel) {
      GenerateCodePanel.currentPanel.panel.reveal(column);
      return GenerateCodePanel.currentPanel;
    }

    const panel = vscode.window.createWebviewPanel(
      'archassistant.generate',
      'Generate Code',
      column || vscode.ViewColumn.One,
      { enableScripts: true, retainContextWhenHidden: true }
    );

    GenerateCodePanel.currentPanel = new GenerateCodePanel(
      panel,
      backendClient,
      projectRegistry,
      storageManager
    );

    GenerateCodePanel.currentPanel.update();
    return GenerateCodePanel.currentPanel;
  }

  private update(): void {
    this.panel.title = 'ArchAssistant: Generate Code';
    this.panel.webview.html = this.getHtmlForWebview();
  }

  private getHtmlForWebview(): string {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); padding: 20px; }
    .form-group { margin-bottom: 14px; }
    label { display: block; margin-bottom: 6px; font-weight: 600; }
    textarea, input, select {
      width: 100%;
      box-sizing: border-box;
      padding: 8px;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border);
      border-radius: 4px;
    }
    textarea { min-height: 140px; font-family: monospace; }
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
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    .result {
      margin-top: 18px;
      padding: 14px;
      background: var(--vscode-textBlockBackground);
      border-radius: 6px;
    }
    .metrics {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-top: 10px;
    }
    .metric {
      padding: 10px;
      background: var(--vscode-badge-background);
      border-radius: 6px;
      text-align: center;
    }
    .metric-value { font-size: 1.2rem; font-weight: 700; }
    .metric-label { font-size: 0.8rem; opacity: 0.8; }
    .file-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px;
      margin-top: 8px;
      background: var(--vscode-list-hoverBackground);
      border-radius: 4px;
    }
    .warning { color: var(--vscode-problemsWarningIcon); margin-top: 6px; }
    .error { color: var(--vscode-problemsErrorIcon); margin-top: 6px; }
  </style>
</head>
<body>
  <h2>Generate Code</h2>

  <div class="form-group">
    <label for="prompt">Prompt</label>
    <textarea id="prompt" placeholder="Describe the code you want to generate..."></textarea>
  </div>

  <div class="form-group">
    <label for="strategy">Strategy</label>
    <select id="strategy">
      <option value="PRE">PRE</option>
      <option value="POST">POST</option>
      <option value="HYBRID" selected>HYBRID</option>
    </select>
  </div>

  <div class="form-group">
    <label for="expectedClassName">Expected Class Name (optional)</label>
    <input type="text" id="expectedClassName" placeholder="e.g., VaccinationService" />
  </div>

  <div class="form-group">
    <label for="maxIterations">Max Iterations</label>
    <input type="number" id="maxIterations" value="3" min="1" max="5" />
  </div>

  <button id="generateBtn" onclick="generate()">Generate</button>

  <div id="result" class="result" style="display:none;">
    <h3>Result</h3>
    <div id="metrics" class="metrics"></div>
    <div id="warnings"></div>
    <div id="files"></div>
    <div style="margin-top: 14px;">
      <button onclick="openSelected()">Open Selected</button>
      <button onclick="saveSelected()">Save Selected</button>
    </div>
  </div>

  <script>
    const vscode = acquireVsCodeApi();
    let generatedFiles = [];

    function generate() {
      const prompt = document.getElementById('prompt').value;
      const strategy = document.getElementById('strategy').value;
      const expectedClassName = document.getElementById('expectedClassName').value;
      const maxIterations = parseInt(document.getElementById('maxIterations').value, 10);

      if (!prompt.trim()) {
        alert('Please enter a prompt');
        return;
      }

      vscode.postMessage({
        command: 'generate',
        request: {
          prompt,
          strategy,
          expectedClassName: expectedClassName || null,
          maxIterations
        }
      });
    }

    function renderResult(response) {
      const resultDiv = document.getElementById('result');
      const metricsDiv = document.getElementById('metrics');
      const warningsDiv = document.getElementById('warnings');
      const filesDiv = document.getElementById('files');

      resultDiv.style.display = 'block';

      const score = response.data?.score?.total;
      const iterations = response.data?.iterations ?? 0;
      const totalTime = response.metadata?.totalTimeMs ?? 0;

      metricsDiv.innerHTML =
        '<div class="metric"><div class="metric-value">' +
        (score === undefined || score === null ? 'N/A' : score.toFixed(0) + '%') +
        '</div><div class="metric-label">Score</div></div>' +
        '<div class="metric"><div class="metric-value">' + iterations + '</div><div class="metric-label">Iterations</div></div>' +
        '<div class="metric"><div class="metric-value">' + (totalTime / 1000).toFixed(1) + 's</div><div class="metric-label">Total Time</div></div>';

      warningsDiv.innerHTML = (response.data?.warnings || [])
        .map((w) => '<div class="warning">⚠️ ' + w + '</div>')
        .join('');

      generatedFiles = response.data?.files || [];
      if (generatedFiles.length === 0 && response.data?.code) {
        generatedFiles = [{
          packageName: '',
          className: 'Generated',
          code: response.data.code,
          artifactKind: 'CLASS'
        }];
      }

      filesDiv.innerHTML = generatedFiles.length
        ? generatedFiles.map((f, i) =>
            '<div class="file-item">' +
            '<input type="checkbox" id="file-' + i + '" checked />' +
            '<span>' + f.className + ' (' + (f.packageName || 'default package') + ')</span>' +
            '</div>'
          ).join('')
        : '<div class="error">No files generated</div>';
    }

    function openSelected() {
      vscode.postMessage({ command: 'openFiles', files: getSelectedFiles() });
    }

    function saveSelected() {
      vscode.postMessage({ command: 'saveFiles', files: getSelectedFiles() });
    }

    function getSelectedFiles() {
      return generatedFiles.filter((_, i) => document.getElementById('file-' + i)?.checked);
    }

    window.addEventListener('message', event => {
      const message = event.data;
      if (message?.type === 'result') {
        renderResult(message.response);
      }
      if (message?.type === 'error') {
        alert(message.message || 'Generation failed');
      }
      if (message?.type === 'generating') {
        document.getElementById('generateBtn').disabled = true;
      }
      if (message?.type === 'done') {
        document.getElementById('generateBtn').disabled = false;
      }
    });
  </script>
</body>
</html>`;
  }

  private async handleMessage(message: any): Promise<void> {
    switch (message?.command) {
      case 'generate':
        await this.handleGenerate(message.request);
        break;
      case 'openFiles':
        await this.handleOpenFiles(message.files || []);
        break;
      case 'saveFiles':
        await this.handleSaveFiles(message.files || []);
        break;
    }
  }

  private async handleGenerate(request: {
    prompt: string;
    strategy: string;
    expectedClassName?: string | null;
    maxIterations?: number;
  }): Promise<void> {
    try {
      this.panel.webview.postMessage({ type: 'generating' });

      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        throw new Error('No project configured. Please configure a project first.');
      }

      const generationRequest: CodeGenerationRequest = {
        prompt: request.prompt,
        projectId: project.projectId,
        strategy: request.strategy as StrategyType,
        maxIterations: request.maxIterations || 3,
        expectedClassName: request.expectedClassName || undefined,
        collectMetrics: true
      };

      const response = await this.backendClient.generateCode(generationRequest);
      const files = response.data?.files?.length
        ? response.data.files
        : this.multiFileParser.parse(response.data?.code || '');

      const resolvedFiles =
        files.length > 0
          ? files
          : [
              {
                packageName: '',
                className: request.expectedClassName || 'Generated',
                code: response.data?.code || '',
                artifactKind: ArtifactKind.CLASS
              }
            ];

      await this.storageManager.setLastGenerationCache({
        projectId: project.projectId,
        code: response.data?.code || '',
        timestamp: new Date().toISOString(),
        strategy: response.data?.strategy || request.strategy,
        score: response.data?.score?.total ?? null
      });

      const patchedResponse: CodeGenerationResponse = {
        ...response,
        data: response.data
          ? { ...response.data, files: resolvedFiles }
          : response.data
      };

      this.panel.webview.postMessage({
        type: 'result',
        response: patchedResponse
      });

      logInfo(`Code generated: ${resolvedFiles.length} file(s)`);
    } catch (error: any) {
      logError(`Generate failed: ${error.message}`);
      this.panel.webview.postMessage({
        type: 'error',
        message: error.message
      });
    } finally {
      this.panel.webview.postMessage({ type: 'done' });
    }
  }

  private async handleOpenFiles(files: GeneratedFile[]): Promise<void> {
    try {
      if (files.length === 0) {
        vscode.window.showWarningMessage('No files selected.');
        return;
      }

      for (const file of files) {
        await this.codeSaver.openGeneratedFile(file);
      }
    } catch (error: any) {
      vscode.window.showErrorMessage(`Failed to open files: ${error.message}`);
    }
  }

  private async handleSaveFiles(files: GeneratedFile[]): Promise<void> {
    try {
      const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
      if (!workspaceFolder) {
        throw new Error('No workspace folder open');
      }

      const results = await this.codeSaver.saveMultipleFiles(files, workspaceFolder);
      const successCount = results.filter((r) => r.success).length;

      vscode.window.showInformationMessage(`Saved ${successCount}/${files.length} file(s)`);

      const firstSaved = results.find((r) => r.success && r.uri);
      if (firstSaved?.uri) {
        const doc = await vscode.workspace.openTextDocument(firstSaved.uri);
        await vscode.window.showTextDocument(doc, {
          preview: false,
          viewColumn: vscode.ViewColumn.One
        });
      }
    } catch (error: any) {
      vscode.window.showErrorMessage(`Failed to save files: ${error.message}`);
    }
  }

  dispose(): void {
    GenerateCodePanel.currentPanel = undefined;
    this.panel.dispose();
  }
}