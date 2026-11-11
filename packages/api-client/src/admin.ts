import createClient from 'openapi-fetch';
import { type ClientOptions, middleware, noStoreFetch } from './client';
import type { paths } from './generated/admin';

/** Client of the admin API (server-side only: the bearer token must never reach the browser). */
export function createAdminClient(options: ClientOptions) {
  const client = createClient<paths>({ baseUrl: options.baseUrl.replace(/\/+$/, ''), fetch: noStoreFetch });
  client.use(middleware(options));
  return client;
}

export type AdminClient = ReturnType<typeof createAdminClient>;
