import createClient from 'openapi-fetch';
import { type ClientOptions, middleware, noStoreFetch } from './client';
import type { paths } from './generated/public';

/** Client of the public API (package list/detail, bookings). */
export function createPublicClient(options: Omit<ClientOptions, 'getToken'>) {
  const client = createClient<paths>({ baseUrl: options.baseUrl.replace(/\/+$/, ''), fetch: noStoreFetch });
  client.use(middleware(options));
  return client;
}

export type PublicClient = ReturnType<typeof createPublicClient>;
