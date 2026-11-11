import { apiUrl, bookingBody, errorBanner, expect, successBanner, test } from '../support/fixtures';

test.describe('출발일 관리', () => {
  test('마감한 출발은 새 예약을 받지 않고, 다시 열면 받는다', async ({ admin, request, firstDeparture }) => {
    const date = await firstDeparture('DAD501');
    await admin.goto(`/admin/products/DAD501/departures?from=${date}`);
    const row = admin.getByRole('row', { name: new RegExp(date) }).first();

    admin.once('dialog', (d) => d.accept());
    await row.getByRole('button', { name: '마감' }).click();
    await expect(successBanner(admin)).toContainText('출발을 마감했습니다');
    await expect(row).toContainText('마감');

    const refused = await request.post(`${apiUrl}/api/v1/package-bookings`, { data: bookingBody('DAD501', date) });
    expect(refused.status()).toBe(422);

    await row.getByRole('button', { name: '다시 열기' }).click();
    await expect(successBanner(admin)).toContainText('다시 열었습니다');
    const accepted = await request.post(`${apiUrl}/api/v1/package-bookings`, { data: bookingBody('DAD501', date) });
    expect(accepted.status()).toBe(201);
  });

  test('정원을 바꾸면 잔여 좌석이 따라가고, 예약된 좌석보다 줄일 수 없다', async ({
    admin,
    firstDeparture,
    createBooking,
  }) => {
    const date = await firstDeparture('DAD501');
    await createBooking('DAD501', date, 4);

    await admin.goto(`/admin/products/DAD501/departures?from=${date}`);
    const row = admin.getByRole('row', { name: new RegExp(date) }).first();

    await row.getByLabel(`${date} 정원`).fill('20');
    await row.getByRole('button', { name: '정원 변경' }).click();
    await expect(successBanner(admin)).toContainText('정원을 20석으로 바꿨습니다');
    await expect(row).toContainText('16'); // 20 - 4

    await row.getByLabel(`${date} 정원`).evaluate((el: HTMLInputElement) => el.removeAttribute('min'));
    await row.getByLabel(`${date} 정원`).fill('2');
    await row.getByRole('button', { name: '정원 변경' }).click();
    await expect(errorBanner(admin)).toBeVisible();
  });

  test('예약이 있는 출발은 취소할 수 없다', async ({ admin, firstDeparture, createBooking }) => {
    const date = await firstDeparture('DAD501');
    await createBooking('DAD501', date);

    await admin.goto(`/admin/products/DAD501/departures?from=${date}`);
    admin.once('dialog', (d) => d.accept());
    await admin
      .getByRole('row', { name: new RegExp(date) })
      .first()
      .getByRole('button', { name: '출발 취소' })
      .click();

    await expect(errorBanner(admin)).toBeVisible();
  });

  test('규칙 밖의 날짜를 추가하면 달력에 나타난다', async ({ admin }) => {
    await admin.goto('/admin/products/TYO201/departures');
    const target = await admin.locator('input[name="date"]').evaluate((el: HTMLInputElement) => {
      // a Tuesday about two weeks out: the sample product departs on Fridays and Sundays
      const d = new Date(el.min);
      d.setDate(d.getDate() + 14);
      while (d.getDay() !== 2) d.setDate(d.getDate() + 1);
      return d.toISOString().slice(0, 10);
    });
    await expect(admin.getByRole('row', { name: new RegExp(target) })).toHaveCount(0);

    await admin.locator('input[name="date"]').fill(target);
    await admin.getByRole('button', { name: '추가' }).click();
    await expect(successBanner(admin)).toContainText(`${target} 출발을 추가했습니다`);
  });
});
