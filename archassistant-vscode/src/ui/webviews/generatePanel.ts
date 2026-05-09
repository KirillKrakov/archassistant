import * as vscode from 'vscode';
import { BackendClient } from '../../backend/BackendClient';
import { ProjectRegistry } from '../../state/projectRegistry';
import { ExtensionState } from '../../state/ExtensionState';
import { CodeGenerationRequest, CodeGenerationResponse, GeneratedFile, StrategyType } from '../../backend/types';
import { MultiFileParser } from '../../services/MultiFileParser';
import { CodeSaver } from '../../services/CodeSaver';
import { logError, logInfo } from '../../utils/logger';

type GeneratedContextSyncResult = {
  success: boolean;
  savedCount?: number;
  totalCount?: number;
  cleared?: boolean;
  sync?: {
    success: boolean;
    projectId: string;
    projectPath?: string;
    syncedFiles?: number;
    compiledSources?: number;
    overlaySourceDir?: string;
    overlayClassesDir?: string;
    contextRefreshed?: boolean;
    warnings?: string[];
    error?: string;
  };
  error?: string;
};

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
    this.panel.webview.onDidReceiveMessage(message => this.handleMessage(message), null, []);
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

    GenerateCodePanel.currentPanel = new GenerateCodePanel(panel, backendClient, projectRegistry, storageManager);
    GenerateCodePanel.currentPanel.update();
    return GenerateCodePanel.currentPanel;
  }

  private update(): void {
    this.panel.title = 'ArchAssistant: Generate Code';
    this.panel.webview.html = this.getHtmlForWebview();
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
.form-group { margin-bottom: 12px; }
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
textarea { min-height: 130px; font-family: monospace; }
button {
  padding: 8px 14px;
  border: none;
  border-radius: 4px;
  background: var(--vscode-button-background);
  color: var(--vscode-button-foreground);
  cursor: pointer;
  font-weight: 600;
  margin-right: 8px;
  margin-top: 4px;
}
button:hover { background: var(--vscode-button-hoverBackground); }
button:disabled { opacity: 0.5; cursor: not-allowed; }

.result {
  margin-top: 16px;
  padding: 12px;
  background: var(--vscode-textBlockBackground);
  border-radius: 6px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}
.metric {
  padding: 8px;
  background: var(--vscode-badge-background);
  border-radius: 4px;
  text-align: center;
}
.metric-value { font-size: 1rem; font-weight: 700; }
.metric-label { font-size: 0.75rem; opacity: 0.8; }
.violations { margin-top: 12px; }
.violation {
  margin-top: 6px;
  padding: 8px;
  border-radius: 4px;
  background: var(--vscode-list-hoverBackground);
  white-space: pre-wrap;
}
.warning { color: var(--vscode-problemsWarningIcon); margin-top: 6px; }
.error { color: var(--vscode-problemsErrorIcon); margin-top: 6px; }
.success { color: var(--vscode-terminal-ansiGreen); margin-top: 6px; }
.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  margin-top: 8px;
  background: var(--vscode-list-hoverBackground);
  border-radius: 4px;
}
.small { opacity: 0.85; font-size: 0.9rem; }
.actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
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

  <div id="status" class="small" style="margin-top: 10px;"></div>

  <div id="result" class="result" style="display:none;">
    <h3>Result</h3>
    <div id="summary" class="small"></div>
    <div id="metrics" class="metrics"></div>
    <div id="warnings"></div>
    <div id="violations" class="violations"></div>
    <div id="files"></div>

    <div class="actions-row">
      <button onclick="openSelected()">Open Selected</button>
      <button onclick="saveSelected()">Save Selected</button>
      <button onclick="saveAndAddToContext()">Save & Add to Context</button>
      <button onclick="clearProjectContext()">Clear Project Context</button>
    </div>
  </div>

<script>
const vscode = acquireVsCodeApi();
let generatedFiles = [];

function setStatus(message) {
  document.getElementById('status').textContent = message || '';
}

function resetResultView() {
  generatedFiles = [];

  document.getElementById('result').style.display = 'none';
  document.getElementById('summary').innerHTML = '';
  document.getElementById('metrics').innerHTML = '';
  document.getElementById('warnings').innerHTML = '';
  document.getElementById('violations').innerHTML = '';
  document.getElementById('files').innerHTML = '';
}

function generate() {
  const prompt = document.getElementById('prompt').value;
  const strategy = document.getElementById('strategy').value;
  const expectedClassName = document.getElementById('expectedClassName').value;
  const maxIterations = parseInt(document.getElementById('maxIterations').value, 10);

  if (!prompt.trim()) {
    alert('Please enter a prompt');
    return;
  }

  resetResultView();
  setStatus('The generation request has been sent, wait for a response.');

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
  setStatus('');
  const resultDiv = document.getElementById('result');
  const metricsDiv = document.getElementById('metrics');
  const warningsDiv = document.getElementById('warnings');
  const violationsDiv = document.getElementById('violations');
  const filesDiv = document.getElementById('files');
  const summaryDiv = document.getElementById('summary');

  resultDiv.style.display = 'block';

  const score = response.data?.score;
  const meta = response.metadata || {};
  summaryDiv.textContent = [
    response.data?.strategy ? ('Strategy: ' + response.data.strategy) : '',
    response.data?.iterations != null ? ('Iterations: ' + response.data.iterations) : '',
    meta.totalTimeMs != null ? ('Total: ' + meta.totalTimeMs + ' ms') : ''
  ].filter(Boolean).join(' · ');

  metricsDiv.innerHTML = [
    ['Score', score?.total],
    ['Rules', score?.rulesPass],
    ['Pattern', score?.patternMatch],
    ['Deps', score?.dependencyCorrect]
  ].map(([label, value]) => \`
    <div class="metric">
      <div class="metric-value">\${value === undefined || value === null ? 'N/A' : Number(value).toFixed(0) + '%'}</div>
      <div class="metric-label">\${label}</div>
    </div>
  \`).join('');

  warningsDiv.innerHTML = (response.data?.warnings || []).map(w => \`<div class="warning">⚠️ \${w}</div>\`).join('');

  const violations = response.data?.score?.violations || [];
  violationsDiv.innerHTML = violations.length
    ? '<h4>Violations</h4>' + violations.map(v => \`
        <div class="violation">
          <strong>\${v.ruleId}</strong> [\${v.severity}]<br/>
          \${v.className ? (v.className + ': ') : ''}\${v.description}
        </div>
      \`).join('')
    : '<div class="success">No violations</div>';

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
    ? generatedFiles.map((f, i) => \`
      <div class="file-item">
        <input type="checkbox" id="file-\${i}" checked />
        <span>\${f.className} (\${f.packageName || 'default package'})</span>
      </div>\`).join('')
    : '<div class="error">No files generated</div>';
}

function renderContextResult(message) {
  const resultDiv = document.getElementById('result');
  const summaryDiv = document.getElementById('summary');
  const warningsDiv = document.getElementById('warnings');
  const filesDiv = document.getElementById('files');

  resultDiv.style.display = 'block';

  if (message?.error) {
    setStatus('');
    summaryDiv.innerHTML = '<div class="error">' + message.error + '</div>';
    return;
  }

  setStatus('');
  summaryDiv.innerHTML = [
    '<div class="success">Selected files were added to the project context.</div>',
    message?.savedCount != null ? '<div>Saved files: ' + message.savedCount + '</div>' : '',
    message?.sync?.syncedFiles != null ? '<div>Synced files: ' + message.sync.syncedFiles + '</div>' : '',
    message?.sync?.overlayClassesDir ? '<div>Overlay classes dir: ' + message.sync.overlayClassesDir + '</div>' : '',
    message?.sync?.contextRefreshed ? '<div>Backend project context refreshed.</div>' : ''
  ].filter(Boolean).join('');

  warningsDiv.innerHTML = (message?.sync?.warnings || [])
    .map(w => \`<div class="warning">⚠️ \${w}</div>\`)
    .join('');

  filesDiv.innerHTML = '';
}

function renderClearResult(message) {
  const resultDiv = document.getElementById('result');
  const summaryDiv = document.getElementById('summary');
  const warningsDiv = document.getElementById('warnings');
  const filesDiv = document.getElementById('files');

  resultDiv.style.display = 'block';

  if (message?.error) {
    setStatus('');
    summaryDiv.innerHTML = '<div class="error">' + message.error + '</div>';
    return;
  }

  setStatus('');
  summaryDiv.innerHTML = '<div class="success">Project context overlay was cleared.</div>' +
    (message?.contextRefreshed ? '<div>Backend project context refreshed.</div>' : '');

  warningsDiv.innerHTML = '';
  filesDiv.innerHTML = '';
}

function openSelected() {
  vscode.postMessage({ command: 'openFiles', files: getSelectedFiles() });
}

function saveSelected() {
  vscode.postMessage({ command: 'saveFiles', files: getSelectedFiles() });
}

function saveAndAddToContext() {
  setStatus('Saving selected files and updating project context...');
  vscode.postMessage({ command: 'addToContext', files: getSelectedFiles() });
}

function clearProjectContext() {
  setStatus('Clearing generated-file overlay from project context...');
  vscode.postMessage({ command: 'clearContext' });
}

function getSelectedFiles() {
  return generatedFiles.filter((_, i) => document.getElementById('file-' + i)?.checked);
}

window.addEventListener('message', event => {
  const message = event.data;

  if (message?.type === 'result') {
    renderResult(message.response);
  } else if (message?.type === 'error') {
    setStatus('');
    document.getElementById('result').style.display = 'block';
    document.getElementById('summary').innerHTML =
      '<div class="error">' + (message.message || 'Generation failed') + '</div>';
  } else if (message?.type === 'contextResult') {
    renderContextResult(message.result);
  } else if (message?.type === 'contextClearResult') {
    renderClearResult(message.result);
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
      case 'addToContext':
        await this.handleSaveAndSyncFiles(message.files || []);
        break;
      case 'clearContext':
        await this.handleClearContext();
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

      await this.storageManager.setLastGenerationCache({
        projectId: project.projectId,
        code: response.data?.code || '',
        timestamp: new Date().toISOString(),
        strategy: response.data?.strategy || 'HYBRID',
        score: response.data?.score?.total ?? null
      });

      const patched: CodeGenerationResponse = {
        ...response,
        data: response.data
          ? {
              ...response.data,
              files
            }
          : response.data
      };

      this.panel.webview.postMessage({
        type: 'result',
        response: patched
      });

      logInfo(`Code generated: ${files.length} file(s), score: ${response.data?.score?.total}`);
    } catch (error: any) {
      logError(`Generate failed: ${error.message}`);
      this.panel.webview.postMessage({
        type: 'error',
        message: error.message
      });
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
      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        throw new Error('No project configured');
      }

      const results = await this.codeSaver.saveMultipleFiles(files, project.projectPath);
      const successCount = results.filter(r => r.success).length;

      vscode.window.showInformationMessage(`Saved ${successCount}/${files.length} file(s)`);

      const firstSaved = results.find(r => r.success && r.uri);
      if (firstSaved?.uri) {
        const doc = await vscode.workspace.openTextDocument(firstSaved.uri);
        await vscode.window.showTextDocument(doc, { preview: false, viewColumn: vscode.ViewColumn.One });
      }
    } catch (error: any) {
      vscode.window.showErrorMessage(`Failed to save files: ${error.message}`);
    }
  }

  private async handleSaveAndSyncFiles(files: GeneratedFile[]): Promise<void> {
    try {
      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        throw new Error('No project configured');
      }

      const backendBaseUrl = this.storageManager.getBackendUrl();
      if (!backendBaseUrl) {
        throw new Error('Backend URL is not configured');
      }

      if (files.length === 0) {
        vscode.window.showWarningMessage('No files selected.');
        this.panel.webview.postMessage({
          type: 'contextResult',
          result: { success: false, error: 'No files selected.' } satisfies GeneratedContextSyncResult
        });
        return;
      }

      const { saved, sync } = await this.codeSaver.saveMultipleFilesAndSync(
        files,
        project.projectPath,
        project.projectId,
        backendBaseUrl
      );

      const savedCount = saved.filter((item) => item.success).length;

      this.panel.webview.postMessage({
        type: 'contextResult',
        result: {
          success: true,
          savedCount,
          totalCount: files.length,
          cleared: false,
          sync
        } satisfies GeneratedContextSyncResult
      });

      if (sync?.success) {
        const warnings = sync.warnings?.length ? ` Warnings: ${sync.warnings.join(' | ')}` : '';
        vscode.window.showInformationMessage(
          `Added ${savedCount}/${files.length} file(s) to project context.${warnings}`
        );
      } else {
        vscode.window.showWarningMessage(
          `Files saved locally, but backend context sync failed${sync?.error ? `: ${sync.error}` : '.'}`
        );
      }
    } catch (error: any) {
      vscode.window.showErrorMessage(`Failed to add files to context: ${error.message}`);
      this.panel.webview.postMessage({
        type: 'contextResult',
        result: {
          success: false,
          error: error.message || 'Failed to add files to context'
        } satisfies GeneratedContextSyncResult
      });
    }
  }

  private async handleClearContext(): Promise<void> {
    try {
      const project = this.projectRegistry.getCurrentProject();
      if (!project) {
        throw new Error('No project configured');
      }

      const backendBaseUrl = this.storageManager.getBackendUrl();
      if (!backendBaseUrl) {
        throw new Error('Backend URL is not configured');
      }

      const result = await this.codeSaver.clearBackendOverlay(project.projectId, backendBaseUrl);

      this.panel.webview.postMessage({
        type: 'contextClearResult',
        result: {
          success: result.success,
          cleared: true
        } satisfies GeneratedContextSyncResult
      });

      vscode.window.showInformationMessage(`Project context overlay cleared for ${project.projectId}`);
    } catch (error: any) {
      vscode.window.showErrorMessage(`Failed to clear project context: ${error.message}`);
      this.panel.webview.postMessage({
        type: 'contextClearResult',
        result: {
          success: false,
          error: error.message || 'Failed to clear project context'
        } satisfies GeneratedContextSyncResult
      });
    }
  }

  dispose(): void {
    GenerateCodePanel.currentPanel = undefined;
    this.panel.dispose();
  }
}