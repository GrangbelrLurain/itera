import { apiUrl, errorBanner, expect, successBanner, test } from '../support/fixtures';

test.describe('상품 등록과 출발 규칙', () => {
  test('상품을 등록하고 일정과 출발 규칙을 넣으면 출발일이 만들어진다', async ({ admin, request }) => {
    await admin.goto('/admin/products/new');
    await admin.getByLabel('상품 코드').fill('E2E100');
    await admin.getByLabel('상품명').fill('E2E 시험 상품');
    await admin.getByLabel('박 *').fill('2');
    await admin.getByLabel('일 *').fill('3');
    await admin.getByPlaceholder('필수').fill('700000');
    await admin.getByRole('button', { name: '상품 등록' }).click();

    await expect(admin).toHaveURL(/\/admin\/products\/E2E100\?created=1/);
    await expect(successBanner(admin)).toContainText('상품을 등록했습니다');

    // itinerary
    await admin.goto('/admin/products/E2E100/itinerary');
    await admin.getByRole('button', { name: '+ 일정 항목 추가' }).click();
    await admin.getByLabel('장소').fill('시험 장소');
    await admin.getByRole('button', { name: '일정 저장' }).click();
    await expect(successBanner(admin)).toContainText('일정 1건을 저장했습니다');

    // departure rule: every Saturday for the next three months
    await admin.goto('/admin/products/E2E100/schedule');
    await admin.getByRole('button', { name: '+ 규칙 추가' }).click();
    const until = new Date();
    until.setMonth(until.getMonth() + 3);
    await admin.getByLabel('종료일').fill(until.toISOString().slice(0, 10));
    await admin.getByText('토', { exact: true }).click();
    await admin.getByRole('button', { name: '출발 규칙 저장' }).click();
    await expect(successBanner(admin)).toContainText('출발 규칙 1건');

    // the calendar shows computed Saturdays, the public API lists them too
    await admin.goto('/admin/products/E2E100/departures');
    await expect(admin.getByRole('row', { name: /\(토\)/ }).first()).toBeVisible();
    const res = await request.get(`${apiUrl}/api/v1/packages`, { params: { keyword: 'E2E100' } });
    expect((await res.json()).total).toBeGreaterThan(0);
  });

  test('이미 있는 상품 코드로 등록하면 오류를 보여주고 입력을 지키지 않는다', async ({ admin }) => {
    await admin.goto('/admin/products/new');
    await admin.getByLabel('상품 코드').fill('DAD501');
    await admin.getByLabel('상품명').fill('중복 시험');
    await admin.getByPlaceholder('필수').fill('700000');
    await admin.getByRole('button', { name: '상품 등록' }).click();

    await expect(errorBanner(admin)).toBeVisible();
    await expect(admin.getByLabel('상품명')).toHaveValue('중복 시험');
  });

  test('상품을 비활성화하면 공개 목록에서 빠진다', async ({ admin, request }) => {
    await admin.goto('/admin/products/TYO201');
    admin.once('dialog', (d) => d.accept());
    await admin.getByRole('button', { name: '상품 비활성화' }).click();
    await expect(successBanner(admin)).toContainText('상품을 비활성화했습니다');

    const res = await request.get(`${apiUrl}/api/v1/packages`, { params: { keyword: 'TYO201' } });
    expect((await res.json()).total).toBe(0);
  });
});
