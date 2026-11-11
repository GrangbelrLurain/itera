import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Workspace packages ship TypeScript sources.
  transpilePackages: ['@itera/api-client', '@itera/domain', '@itera/ui', '@itera/theme', '@itera/token'],
  // Standalone output for the Docker image (monorepo root so server.js lands in apps/admin/).
  output: 'standalone',
  outputFileTracingRoot: path.join(here, '../..'),
};
export default nextConfig;
