import axios, { AxiosInstance } from 'axios';
import * as types from './types';
import { handleBackendError } from './errors';

export class BackendClient {
  private readonly client: AxiosInstance;

  constructor(private baseUrl: string) {
    this.client = axios.create({
      baseURL: baseUrl,
      timeout: 60000,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  updateBaseUrl(newUrl: string): void {
    this.baseUrl = newUrl;
    this.client.defaults.baseURL = newUrl;
  }

  async checkHealth(): Promise<types.HealthResponse> {
    try {
      const response = await this.client.get<types.HealthResponse>('/api/health');
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async getRules(projectId: string): Promise<types.RulesConfig> {
    try {
      const response = await this.client.get<types.RulesConfig>(`/api/rules/${projectId}`);
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async saveRules(config: types.RulesConfig): Promise<{ success: boolean }> {
    try {
      const response = await this.client.post<{ success: boolean }>(
        `/api/rules/${config.project_id}`,
        config
      );
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async suggestRules(projectId: string, projectPath: string): Promise<types.WorkspaceModuleSuggestions[]> {
    try {
      const response = await this.client.get<types.WorkspaceModuleSuggestions[]>(
        `/api/rules/${projectId}/suggest`,
        { params: { projectPath } }
      );
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async saveProjectPath(projectId: string, projectPath: string): Promise<{ success: boolean }> {
    try {
      const response = await this.client.post<{ success: boolean }>(
        `/api/rules/${projectId}/path`,
        { projectPath }
      );
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async deleteRules(projectId: string): Promise<{ success: boolean }> {
    try {
      const response = await this.client.delete<{ success: boolean }>(`/api/rules/${projectId}`);
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async generateCode(request: types.CodeGenerationRequest): Promise<types.CodeGenerationResponse> {
    try {
      const response = await this.client.post<types.CodeGenerationResponse>('/api/generate', request);
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async getProjectMetrics(projectId: string): Promise<types.ProjectMetrics> {
    try {
      const response = await this.client.get<types.ProjectMetrics>(`/api/metrics/${projectId}`);
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async compareStrategies(projectId?: string): Promise<types.ComparisonResult> {
    try {
      const response = await this.client.get<types.ComparisonResult>('/api/metrics/compare', {
        params: { projectId }
      });
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async exportMetrics(request: types.ExportRequest): Promise<Uint8Array> {
    try {
      const response = await this.client.post<ArrayBuffer>(
        '/api/metrics/export',
        request,
        { responseType: 'arraybuffer' }
      );
      return new Uint8Array(response.data);
    } catch (error) {
      throw handleBackendError(error);
    }
  }

  async getGenerationHistory(
    projectId: string,
    page = 0,
    size = 20
  ): Promise<{ totalRecords: number; totalPages: number; records: types.GenerationHistoryItem[] }> {
    try {
      const response = await this.client.get(`/api/metrics/${projectId}/history`, {
        params: { page, size }
      });
      return response.data;
    } catch (error) {
      throw handleBackendError(error);
    }
  }
}