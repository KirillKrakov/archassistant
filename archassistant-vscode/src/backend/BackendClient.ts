import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import * as types from './types';

export class BackendError extends Error {
  constructor(
    message: string,
    public readonly code?: string,
    public readonly statusCode?: number
  ) {
    super(message);
    this.name = 'BackendError';
  }
}

export class BackendClient {
  private readonly client: AxiosInstance;

  constructor(baseUrl: string) {
    this.client = axios.create({
      baseURL: baseUrl,
      timeout: 60000,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  updateBaseUrl(newUrl: string): void {
    this.client.defaults.baseURL = newUrl;
  }

  async health(): Promise<types.HealthResponse> {
    return this.request<types.HealthResponse>('/api/health', { method: 'GET' });
  }

  async checkHealth(): Promise<types.HealthResponse> {
    return this.health();
  }

  async getRules(projectId: string): Promise<types.RulesConfig> {
    return this.request<types.RulesConfig>(`/api/rules/${encodeURIComponent(projectId)}`, {
      method: 'GET'
    });
  }

  async saveRules(projectId: string, config: types.RulesConfig): Promise<types.BackendSuccessResponse> {
    return this.request<types.BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}`, {
      method: 'POST',
      data: config
    });
  }

  async deleteRules(projectId: string): Promise<types.BackendSuccessResponse> {
    return this.request<types.BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}`, {
      method: 'DELETE'
    });
  }

  async suggestRules(projectId: string, projectPath: string): Promise<types.WorkspaceModuleSuggestions[]> {
    return this.request<types.WorkspaceModuleSuggestions[]>(`/api/rules/${encodeURIComponent(projectId)}/suggest`, {
      method: 'GET',
      params: { projectPath }
    });
  }

  async saveProjectPath(projectId: string, projectPath: string): Promise<types.BackendSuccessResponse> {
    return this.request<types.BackendSuccessResponse>(`/api/rules/${encodeURIComponent(projectId)}/path`, {
      method: 'POST',
      data: { projectPath }
    });
  }

  async generateCode(request: types.CodeGenerationRequest): Promise<types.CodeGenerationResponse> {
    return this.request<types.CodeGenerationResponse>('/api/generate', {
      method: 'POST',
      data: request
    });
  }

  async getProjectMetrics(projectId: string): Promise<types.ProjectMetrics> {
    return this.request<types.ProjectMetrics>(`/api/metrics/${encodeURIComponent(projectId)}`, {
      method: 'GET'
    });
  }

  async compareStrategies(projectId?: string): Promise<types.ComparisonResult> {
    return this.request<types.ComparisonResult>('/api/metrics/compare', {
      method: 'GET',
      params: projectId ? { projectId } : undefined
    });
  }

  async exportMetrics(request: types.ExportRequest): Promise<Uint8Array> {
    try {
      const response = await this.client.post<ArrayBuffer>('/api/metrics/export', request, {
        responseType: 'arraybuffer'
      });
      return new Uint8Array(response.data);
    } catch (error) {
      throw this.toBackendError(error);
    }
  }

  async getGenerationHistory(
    projectId: string,
    page = 0,
    size = 20
  ): Promise<{
    totalRecords: number;
    totalPages: number;
    records: types.GenerationHistoryItem[];
  }> {
    return this.request(`/api/metrics/${encodeURIComponent(projectId)}/history`, {
      method: 'GET',
      params: { page, size }
    });
  }

  private async request<T>(path: string, config: AxiosRequestConfig = {}): Promise<T> {
    try {
      const response = await this.client.request<T>({
        ...config,
        url: path
      });
      return response.data;
    } catch (error) {
      throw this.toBackendError(error);
    }
  }

  private toBackendError(error: unknown): BackendError {
    const anyError = error as any;

    if (anyError instanceof BackendError) {
      return anyError;
    }

    if (anyError?.response) {
      const status = anyError.response.status as number | undefined;
      const data = anyError.response.data;
      const message = data?.error || data?.message || anyError.message || 'Request failed';
      return new BackendError(message, data?.code, status);
    }

    if (anyError?.code === 'ECONNREFUSED' || anyError?.code === 'ENOTFOUND') {
      return new BackendError(`Cannot connect to backend: ${anyError.message}`, 'CONNECTION_ERROR');
    }

    return new BackendError(anyError?.message || 'Unknown backend error');
  }
}