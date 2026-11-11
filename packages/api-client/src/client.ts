import type { Client, Middleware } from 'openapi-fetch';
import { toApiError } from './errors';

export interface ClientOptions {
  /** Origin of the backend, e.g. http://backend:8080 (the paths of the contract already contain /api/v1). */
  baseUrl: string;
  /** Bearer token for protected routes; resolved for every request. */
  getToken?: () => string | undefined | Promise<string | undefined>;
}

/** Adds the bearer token and turns every non-2xx response into an ApiError. */
export function middleware(options: ClientOptions): Middleware {
  return {
    async onRequest({ request }) {
      const token = await options.getToken?.();
      if (token) request.headers.set('Authorization', `Bearer ${token}`);
      return request;
    },
    async onResponse({ response }) {
      if (!response.ok) throw await toApiError(response);
      return response;
    },
  };
}

/** Responses are never cached here; callers opt in per request where it makes sense. */
export const noStoreFetch = (request: Request) => fetch(request, { cache: 'no-store' });

/**
 * The client throws ApiError for failed responses, so `data` is set on success.
 * Use this to get it without the `undefined` of the generic result type.
 */
export async function unwrap<T>(result: Promise<{ data?: T }>): Promise<T> {
  const { data } = await result;
  return data as T;
}

export type { Client };
