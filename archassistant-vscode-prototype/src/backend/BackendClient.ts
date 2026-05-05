import { BackendSuccessResponse, HealthResponse, RulesConfig, WorkspaceModuleSuggestions } from './types';

export class BackendError extends Error {
  constructor(message: string, public readonly status?: number) {
    super(message);
    this.name = 'BackendError';
  }
}

export class BackendClient {
  constructor(private readonly baseUrl: string) {}

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);

    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...init,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          ...(init?.headers ?? {})
        }
      });

      const text = await response.text();
      const payload = text ? safeJsonParse(text) : null;

      if (!response.ok) {
        const message = payload?.error || payload?.message || response.statusText;
        throw new BackendError(message || `Request failed with status ${response.status}`, response.status);
      }

      return payload as T;
    } catch (error) {
      if (error instanceof BackendError) {
        throw error;
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new BackendError('Request timed out');
      }
      throw new BackendError(error instanceof Error ? error.message : 'Unknown backend error');
    } finally {
      clearTimeout(timeout);
    }
  }

  async health(): Promise<HealthResponse> {
    return this.request<HealthResponse>('/api/health');
  }

  async saveProjectPath(projectId: string, projectPath: string): Promise<BackendSuccessResponse> {
    return this.request<BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}/path`, {
      method: 'POST',
      body: JSON.stringify({ projectPath })
    });
  }

  async getRules(projectId: string): Promise<RulesConfig> {
    return this.request<RulesConfig>(`/api/rules/${encodeURIComponent(projectId)}`);
  }

  async saveRules(projectId: string, config: RulesConfig): Promise<BackendSuccessResponse> {
    return this.request<BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}`, {
      method: 'POST',
      body: JSON.stringify(config)
    });
  }

  async deleteRules(projectId: string): Promise<BackendSuccessResponse> {
    return this.request<BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}`, {
      method: 'DELETE'
    });
  }

  async suggestRules(projectId: string, projectPath?: string): Promise<WorkspaceModuleSuggestions[]> {
    const query = projectPath ? `?projectPath=${encodeURIComponent(projectPath)}` : '';
    return this.request<WorkspaceModuleSuggestions[]>(`/api/rules/${encodeURIComponent(projectId)}/suggest${query}`);
  }
}

function safeJsonParse(value: string): any {
  try {
    return JSON.parse(value);
  } catch {
    return { raw: value };
  }
}
