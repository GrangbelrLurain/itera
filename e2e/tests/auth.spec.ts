import { ADMIN, errorBanner, expect, test } from '../support/fixtures';

test.describe('로그인', () => {
  test('잘못된 비밀번호는 오류를 보여주고 로그인 화면에 머문다', async ({ page }) => {
    await page.goto('/admin/login');
    await page.getByLabel('아이디').fill(ADMIN.username);
    await page.getByLabel('비밀번호').fill('wrong-password');
    await page.getByRole('button', { name: '로그인' }).click();

    await expect(errorBanner(page)).toContainText('아이디 또는 비밀번호가 올바르지 않습니다.');
    await expect(page).toHaveURL(/\/admin\/login/);
  });

  test('로그인하면 대시보드가 열리고 로그아웃하면 다시 로그인 화면이다', async ({ admin }) => {
    await expect(admin.getByText('예약 현황 요약입니다.')).toBeVisible();

    await admin.getByRole('button', { name: '로그아웃' }).click();
    await expect(admin).toHaveURL(/\/admin\/login/);

    // the session is gone: a protected page sends the visitor back to the login screen
    await admin.goto('/admin/bookings');
    await expect(admin).toHaveURL(/\/admin\/login/);
  });

  test('로그인하지 않으면 보호된 화면이 로그인으로 보낸다', async ({ page }) => {
    await page.goto('/admin/products');
    await expect(page).toHaveURL(/\/admin\/login/);
  });
});
