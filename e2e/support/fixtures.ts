import { test as base, expect, type Page } from '@playwright/test';

export const apiUrl = process.env.E2E_API_URL ?? 'http://localhost:18080';

/** Account of the memory profile (application-memory.properties). */
export const ADMIN = { username: 'admin', password: process.env.ADMIN_PASSWORD ?? 'e2e-admin-password' };

type Departure = { productCode: string; departureDate: string };

/** Booking body for the public booking API. */
export function bookingBody(productCode: string, departureDate: string, travelers = 1, channelCode = 'DIRECT') {
  return {
    productCode,
    departureDate,
    channelCode,
    contact: { name: '예약자', phone: '010-1111-2222' },
    travelers: Array.from({ length: travelers }, (_, i) => ({
      targetType: 'ADULT',
      name: `여행자${i + 1}`,
      birthDate: '1985-05-05',
      gender: 'M',
    })),
  };
}

export const test = base.extend<{
  /** Resets the backend to its sample data before the test. */
  freshData: undefined;
  /** First bookable departure date of a product (from the public API). */
  firstDeparture: (productCode: string) => Promise<string>;
  /** Creates a booking through the public API and returns its code. */
  createBooking: (productCode: string, date: string, travelers?: number, channel?: string) => Promise<string>;
  /** A page that is already signed in to the admin. */
  admin: Page;
}>({
  freshData: [
    async ({ request }, use) => {
      const res = await request.post(`${apiUrl}/api/test/reset`);
      expect(res.status()).toBe(204);
      await use(undefined);
    },
    { auto: true },
  ],

  firstDeparture: async ({ request }, use) => {
    await use(async (productCode) => {
      const res = await request.get(`${apiUrl}/api/v1/packages`, { params: { keyword: productCode } });
      const items = (await res.json()).items as Departure[];
      const first = items.find((i) => i.productCode === productCode);
      expect(first, `a departure of ${productCode}`).toBeDefined();
      return (first as Departure).departureDate;
    });
  },

  createBooking: async ({ request }, use) => {
    await use(async (productCode, date, travelers = 1, channel = 'DIRECT') => {
      const res = await request.post(`${apiUrl}/api/v1/package-bookings`, {
        data: bookingBody(productCode, date, travelers, channel),
      });
      expect(res.status()).toBe(201);
      return (await res.json()).bookingCode as string;
    });
  },

  admin: async ({ page }, use) => {
    await page.goto('/admin/login');
    await page.getByLabel('아이디').fill(ADMIN.username);
    await page.getByLabel('비밀번호').fill(ADMIN.password);
    await page.getByRole('button', { name: '로그인' }).click();
    await expect(page.getByRole('heading', { name: '대시보드' })).toBeVisible();
    await use(page);
  },
});

/** Error banner of the page (Next.js adds its own role=alert route announcer, which is not a message). */
export const errorBanner = (page: Page) => page.locator('[role="alert"]:not(#__next-route-announcer__)');

/** Success banner of the page. */
export const successBanner = (page: Page) => page.locator('[role="status"]');

export { expect };
