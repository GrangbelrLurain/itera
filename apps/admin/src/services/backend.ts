/**
 * Origin of the backend, read by Server Components and Server Actions only (the browser never calls it).
 * Inside Docker it is http://backend:8080; the API paths of the contract already contain /api/v1.
 */
export function backendOrigin(): string {
  return (process.env.API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
}
