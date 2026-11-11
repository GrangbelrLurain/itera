import { type AdminOps, type AdminSchemas, ApiError, createAdminClient, unwrap } from '@itera/api-client';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { backendOrigin } from '@/services/backend';

// Server-only admin API access. The bearer token lives in an httpOnly cookie, so browser code never sees it.
export const ADMIN_COOKIE = 'aerocore_admin';

export { ApiError };

type S = AdminSchemas;
export type AdminDashboard = S['Dashboard'];
export type AdminBookingList = S['AdminBookingPage'];
export type AdminBookingSummary = S['BookingSummary'];
export type AdminLoginResponse = S['LoginResponse'];
export type BookingDetail = S['PackageBooking'];
export type AdminBookingQuery = NonNullable<AdminOps['listAdminBookings']['parameters']['query']>;

export type ProductInput = S['ProductInput'];
export type PriceInput = S['PriceInput'];
export type ProductDetail = S['ProductDetail'];
export type ProductSummary = S['ProductSummary'];
export type ItineraryInput = S['ItineraryInput'];
export type ScheduleInput = S['ScheduleInput'];
export type RuleInput = S['RuleInput'];
export type ExtrasInput = S['ExtrasInput'];
export type OptionalTourInput = S['OptionalTourInput'];
export type ShoppingInput = S['ShoppingInput'];
export type RemarkInput = S['RemarkInput'];
export type ConditionInput = S['ConditionInput'];
export type DepartureItem = S['DepartureItem'];
export type PricingView = S['PricingView'];
export type PricingInput = S['PricingInput'];
export type CostInput = S['CostInput'];
export type PricingPreview = S['PricingPreview'];
export type PreviewRow = S['PreviewRow'];
export type CommissionInput = S['CommissionInput'];
export type CostOverrideView = S['CostOverrideView'];
export type AdminBookingDetail = S['AdminBookingDetail'];

async function token(): Promise<string> {
  const value = (await cookies()).get(ADMIN_COOKIE)?.value;
  if (!value) redirect('/admin/login');
  return value;
}

const client = createAdminClient({ baseUrl: backendOrigin(), getToken: token });
const anonymous = createAdminClient({ baseUrl: backendOrigin() });

/** Runs a call; an expired or invalid token sends the user back to the login page. */
async function call<T>(result: Promise<{ data?: T }>): Promise<T> {
  try {
    return await unwrap(result);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) redirect('/admin/login?expired=1');
    throw e;
  }
}

/** Login does not need a token; returns it so the caller can store it in a cookie. */
export async function adminLogin(username: string, password: string): Promise<AdminLoginResponse> {
  return unwrap(anonymous.POST('/api/v1/admin/login', { body: { username, password } }));
}

export const adminApi = {
  dashboard: () => call(client.GET('/api/v1/admin/dashboard')),
  listBookings: (query: AdminBookingQuery) => call(client.GET('/api/v1/admin/bookings', { params: { query } })),
  getBooking: (bookingCode: string) =>
    call(client.GET('/api/v1/admin/bookings/{bookingCode}', { params: { path: { bookingCode } } })),
  confirmPayment: (bookingCode: string, paymentReference: string) =>
    call(
      client.POST('/api/v1/admin/bookings/{bookingCode}/pay', {
        params: { path: { bookingCode } },
        body: { paymentReference },
      }),
    ),
  cancelBooking: (bookingCode: string, reason?: string) =>
    call(
      client.POST('/api/v1/admin/bookings/{bookingCode}/cancel', {
        params: { path: { bookingCode } },
        body: { reason },
      }),
    ),

  // ---- products, schedule, departures
  listProducts: () => call(client.GET('/api/v1/admin/products')),
  getProduct: (productCode: string) =>
    call(client.GET('/api/v1/admin/products/{productCode}', { params: { path: { productCode } } })),
  createProduct: (body: ProductInput) => call(client.POST('/api/v1/admin/products', { body })),
  updateProduct: (productCode: string, body: ProductInput) =>
    call(client.PUT('/api/v1/admin/products/{productCode}', { params: { path: { productCode } }, body })),
  setProductActive: (productCode: string, active: boolean) =>
    call(
      client.POST('/api/v1/admin/products/{productCode}/active', {
        params: { path: { productCode } },
        body: { active },
      }),
    ),
  saveItinerary: (productCode: string, rows: ItineraryInput[]) =>
    call(
      client.PUT('/api/v1/admin/products/{productCode}/itinerary', {
        params: { path: { productCode } },
        body: { rows },
      }),
    ),
  saveSchedule: (productCode: string, body: ScheduleInput) =>
    call(client.PUT('/api/v1/admin/products/{productCode}/schedule', { params: { path: { productCode } }, body })),
  saveExtras: (productCode: string, body: ExtrasInput) =>
    call(client.PUT('/api/v1/admin/products/{productCode}/extras', { params: { path: { productCode } }, body })),
  listDepartures: (productCode: string, query: { from?: string; to?: string } = {}) =>
    call(client.GET('/api/v1/admin/products/{productCode}/departures', { params: { path: { productCode }, query } })),
  addDeparture: (productCode: string, date: string) =>
    call(
      client.POST('/api/v1/admin/products/{productCode}/departures', {
        params: { path: { productCode } },
        body: { date },
      }),
    ),
  closeDeparture: (productCode: string, date: string) =>
    call(
      client.POST('/api/v1/admin/products/{productCode}/departures/{date}/close', {
        params: { path: { productCode, date } },
      }),
    ),
  reopenDeparture: (productCode: string, date: string) =>
    call(
      client.POST('/api/v1/admin/products/{productCode}/departures/{date}/reopen', {
        params: { path: { productCode, date } },
      }),
    ),
  cancelDeparture: (productCode: string, date: string) =>
    call(
      client.POST('/api/v1/admin/products/{productCode}/departures/{date}/cancel', {
        params: { path: { productCode, date } },
      }),
    ),
  setDepartureCapacity: (productCode: string, date: string, capacity: number) =>
    call(
      client.PUT('/api/v1/admin/products/{productCode}/departures/{date}/capacity', {
        params: { path: { productCode, date } },
        body: { capacity },
      }),
    ),

  // ---- pricing
  getPricing: (productCode: string) =>
    call(client.GET('/api/v1/admin/products/{productCode}/pricing', { params: { path: { productCode } } })),
  savePricing: (productCode: string, body: PricingInput) =>
    call(client.PUT('/api/v1/admin/products/{productCode}/pricing', { params: { path: { productCode } }, body })),
  previewPricing: (productCode: string, channel?: string, date?: string) =>
    call(
      client.GET('/api/v1/admin/products/{productCode}/pricing/preview', {
        params: { path: { productCode }, query: { channel, date } },
      }),
    ),
  getCostOverrides: (productCode: string, date: string) =>
    call(
      client.GET('/api/v1/admin/products/{productCode}/departures/{date}/cost-overrides', {
        params: { path: { productCode, date } },
      }),
    ),
  saveCostOverrides: (productCode: string, date: string, rows: CostInput[]) =>
    call(
      client.PUT('/api/v1/admin/products/{productCode}/departures/{date}/cost-overrides', {
        params: { path: { productCode, date } },
        body: { rows },
      }),
    ),
  listCommissions: () => call(client.GET('/api/v1/admin/channel-commissions')),
  replaceCommissions: (rows: CommissionInput[]) =>
    call(client.PUT('/api/v1/admin/channel-commissions', { body: { rows } })),
};
