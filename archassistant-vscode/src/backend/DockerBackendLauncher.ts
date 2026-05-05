import * as cp from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import { promisify } from 'util';
import { BackendClient } from './BackendClient';

const execFile = promisify(cp.execFile);

export interface DockerBackendLaunchOptions {
  projectPath: string;
  composeDirectory: string;
  serviceName: string;
  backendUrl: string;
  dockerBinary?: string;
}

export class DockerBackendLauncher {
  async start(options: DockerBackendLaunchOptions): Promise<void> {
    if (!options.composeDirectory) {
      throw new Error('docker compose directory is not configured');
    }

    const composeFile = this.findComposeFile(options.composeDirectory);
    if (!composeFile) {
      throw new Error(
        `docker-compose.yml or compose.yml not found in ${options.composeDirectory}`
      );
    }

    const dockerBinary = options.dockerBinary ?? (process.platform === 'win32' ? 'docker.exe' : 'docker');

    const env = {
      ...process.env,
      PROJECT_PATH: options.projectPath
    };

    await execFile(
      dockerBinary,
      ['compose', '-f', composeFile, 'up', '-d', options.serviceName],
      {
        cwd: options.composeDirectory,
        env,
        windowsHide: true,
        maxBuffer: 10 * 1024 * 1024
      }
    );

    await this.waitForHealthy(options.backendUrl);
  }

  private async waitForHealthy(baseUrl: string, attempts = 30, delayMs = 1000): Promise<void> {
    const client = new BackendClient(baseUrl);
    let lastError: unknown;

    for (let i = 0; i < attempts; i++) {
      try {
        const health = await client.health();
        if (health?.status?.toUpperCase() === 'UP') return;
      } catch (error) {
        lastError = error;
      }

      await this.delay(delayMs);
    }

    throw new Error(
      `Backend did not become healthy: ${
        lastError instanceof Error ? lastError.message : 'unknown error'
      }`
    );
  }

  private findComposeFile(dir: string): string | null {
    const candidates = ['docker-compose.yml', 'docker-compose.yaml', 'compose.yml', 'compose.yaml'];
    for (const candidate of candidates) {
      const full = path.join(dir, candidate);
      if (fs.existsSync(full)) return full;
    }
    return null;
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}