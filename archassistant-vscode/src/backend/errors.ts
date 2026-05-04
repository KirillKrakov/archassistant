export class BackendError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly statusCode?: number
  ) {
    super(message);
    this.name = 'BackendError';
  }
}

export class ConnectionError extends BackendError {
  constructor(message = 'Cannot connect to backend') {
    super('CONNECTION_ERROR', message, 0);
  }
}

export class ValidationError extends BackendError {
  constructor(message: string) {
    super('VALIDATION_ERROR', message, 400);
  }
}

export class NotFoundError extends BackendError {
  constructor(resource: string) {
    super('NOT_FOUND', `${resource} not found`, 404);
  }
}

export class ServerError extends BackendError {
  constructor(message = 'Internal server error') {
    super('SERVER_ERROR', message, 500);
  }
}

export function handleBackendError(error: any): BackendError {
  if (error instanceof BackendError) return error;

  if (error?.response) {
    const status = error.response.status;
    const data = error.response.data;
    if (status === 400) return new ValidationError(data?.error || data?.message || 'Validation failed');
    if (status === 404) return new NotFoundError('Resource');
    if (status >= 500) return new ServerError(data?.error || data?.message || 'Server error');
  }

  if (error?.code === 'ECONNREFUSED' || error?.code === 'ENOTFOUND' || error?.code === 'ETIMEDOUT') {
    return new ConnectionError(`Cannot connect to backend: ${error.message}`);
  }

  return new ServerError(error?.message || 'Unknown error');
}