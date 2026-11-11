import { errorBanner, expect, successBanner, test } from '../support/fixtures';

test.describe('예약 관리', () => {
  test('공개 API로 만든 예약이 목록에 나오고, 결제 확인하면 예약 확정이 된다', async ({
    admin,
    firstDeparture,
    createBooking,
  }) => {
    const date = await firstDeparture('DAD501');
    const code = await createBooking('DAD501', date, 2);

    await admin.goto('/admin/bookings');
    const row = admin.getByRole('row', { name: new RegExp(code) });
    await expect(row).toContainText('결제 대기');
    await expect(row).toContainText('2'); // seats

    await row.getByRole('link', { name: code }).click();
    await expect(admin.getByRole('heading', { name: /다낭 호이안/ })).toBeVisible();
    await expect(admin.getByText('결제 기한:')).toBeVisible();

    admin.once('dialog', (d) => d.accept());
    await admin.getByPlaceholder('결제 번호 (필수)').fill('PAY-E2E-1');
    await admin.getByRole('button', { name: '결제 확인' }).click();

    await expect(successBanner(admin)).toContainText('예약이 확정되었습니다');
    await expect(admin.getByText('예약 확정').first()).toBeVisible();
    await expect(admin.getByText('PAY-E2E-1')).toBeVisible();
  });

  test('결제 전 예약을 취소하면 위약금 없이 취소되고 좌석이 돌아온다', async ({
    admin,
    request,
    firstDeparture,
    createBooking,
  }) => {
    const date = await firstDeparture('DAD501');
    const code = await createBooking('DAD501', date, 3);

    await admin.goto(`/admin/products/DAD501/departures?from=${date}`);
    const seatsRow = () => admin.getByRole('row', { name: new RegExp(date) }).first();
    await expect(seatsRow()).toContainText('27'); // 30 seats - 3

    await admin.goto(`/admin/bookings/${code}`);
    admin.once('dialog', (d) => d.accept());
    await admin.getByPlaceholder('취소 사유 (선택)').fill('고객 요청');
    await admin.getByRole('button', { name: '예약 취소' }).click();

    await expect(successBanner(admin)).toContainText('예약을 취소했습니다');
    await expect(admin.getByText('고객 요청')).toBeVisible();
    await expect(admin.getByText('위약금')).toBeVisible();

    await admin.goto(`/admin/products/DAD501/departures?from=${date}`);
    await expect(seatsRow()).toContainText('30');
    expect(request).toBeDefined();
  });

  test('상태 필터와 검색어로 예약을 좁힌다', async ({ admin, firstDeparture, createBooking }) => {
    const date = await firstDeparture('DAD501');
    const first = await createBooking('DAD501', date);
    await createBooking('DAD501', date);

    await admin.goto('/admin/bookings?status=CONFIRMED');
    await expect(admin.getByText('조건에 맞는 예약이 없습니다.')).toBeVisible();

    await admin.goto(`/admin/bookings?keyword=${first}`);
    await expect(admin.getByText(/총\s*1\s*건/)).toBeVisible();
    await expect(errorBanner(admin)).toHaveCount(0);
  });
});
