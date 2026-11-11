/** Every non-2xx answer of the backend, normalized from its {error, code} body. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Reads the {error, code} body of a failed response (falls back to the HTTP status). */
export async function toApiError(response: Response): Promise<ApiError> {
  let message = `HTTP ${response.status}`;
  let code: string | undefined;
  try {
    const body = (await response.clone().json()) as { error?: string; code?: string };
    if (body.error) message = body.error;
    code = body.code;
  } catch {
    // not JSON: keep the status text
  }
  return new ApiError(response.status, code, message);
}
