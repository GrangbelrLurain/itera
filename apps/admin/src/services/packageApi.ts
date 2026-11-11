import { ApiError, createPublicClient, type PublicOps, type PublicSchemas, unwrap } from '@itera/api-client';
import { backendOrigin } from '@/services/backend';

export { ApiError };
export type PackageSummary = PublicSchemas['PackageSummary'];
export type PackageList = PublicSchemas['PackageSummaryPage'];
export type PackageDetail = PublicSchemas['PackageDetail'];
export type PackagePrice = PublicSchemas['PackagePrice'];
export type PackageBooking = PublicSchemas['PackageBooking'];
export type PackageListQuery = NonNullable<PublicOps['listPackages']['parameters']['query']>;

// Server-side access to the public API (Server Components).
const client = createPublicClient({ baseUrl: backendOrigin() });

export function listPackages(query: PackageListQuery = {}): Promise<PackageList> {
  return unwrap(client.GET('/api/v1/packages', { params: { query } }));
}

export function getPackage(productCode: string, departureDate: string): Promise<PackageDetail> {
  return unwrap(
    client.GET('/api/v1/packages/{productCode}/departures/{departureDate}', {
      params: { path: { productCode, departureDate } },
    }),
  );
}

/** Link to the admin detail page of a departure. */
export function departureHref(productCode: string, departureDate: string): string {
  return `/admin/packages/${encodeURIComponent(productCode)}/${departureDate}`;
}
