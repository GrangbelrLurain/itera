import type { components as AdminComponents, operations as AdminOperations } from './generated/admin';
import type { components as PublicComponents, operations as PublicOperations } from './generated/public';

export { type AdminClient, createAdminClient } from './admin';
export { unwrap } from './client';
export { ApiError } from './errors';
export { createPublicClient, type PublicClient } from './public';

/** Schemas and operations of the generated contracts, by API group. */
export type AdminSchemas = AdminComponents['schemas'];
export type AdminOps = AdminOperations;
export type PublicSchemas = PublicComponents['schemas'];
export type PublicOps = PublicOperations;
