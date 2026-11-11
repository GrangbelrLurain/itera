import type { Page } from '@playwright/test';
import { ADMIN, expect, test } from '../support/fixtures';

/**
 * The page inside the desktop shell. The real shell is a Tauri window; here its two hooks are stubbed:
 * `window.__ITERA_SHELL__` (set by the shell before any script runs) and the IPC entry `__TAURI_INTERNALS__.invoke`
 * that records every call. Native behavior (frame, drag, maximize) is checked by running the shell itself.
 */
type Call = { cmd: string; args: Record<string, unknown> };

async function pretendToBeShell(page: Page, platform: 'windows' | 'macos' | 'linux', refuseChrome = false) {
  await page.addInitScript(
    ({ platform, refuseChrome }) => {
      const calls: Call[] = [];
      Object.assign(window, { __calls: calls });
      Object.defineProperty(window, '__ITERA_SHELL__', { value: { platform, version: '1.0.0' } });
      Object.defineProperty(window, '__TAURI_INTERNALS__', {
        value: {
          metadata: { currentWindow: { label: 'main' }, currentWebview: { windowLabel: 'main', label: 'main' } },
          invoke: async (cmd: string, args: Record<string, unknown>) => {
            calls.push({ cmd, args });
            if (cmd === 'use_custom_chrome' && refuseChrome) throw new Error('not allowed');
            if (cmd === 'plugin:window|is_maximized') return false;
            return null;
          },
        },
      });
    },
    { platform, refuseChrome },
  );
}

const calls = (page: Page) => page.evaluate(() => (window as unknown as { __calls: Call[] }).__calls);
const titleBar = (page: Page) => page.locator('[data-tauri-drag-region]');

async function login(page: Page) {
  await page.goto('/admin/login');
  await page.getByLabel('아이디').fill(ADMIN.username);
  await page.getByLabel('비밀번호').fill(ADMIN.password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page.getByRole('heading', { name: '대시보드' })).toBeVisible();
}

test.describe('데스크톱 셸 안의 화면', () => {
  test('일반 브라우저에는 셸 제목 표시줄이 없다', async ({ page }) => {
    await page.goto('/admin/login');
    await expect(page.getByText('Itera 관리자')).toBeVisible();
    await expect(titleBar(page)).toHaveCount(0);
  });

  test('Windows 셸: 제목 표시줄을 그리고 셸에 네이티브 프레임을 끄도록 요청한다', async ({ page }) => {
    await pretendToBeShell(page, 'windows');
    await login(page);

    const bar = titleBar(page);
    await expect(bar).toBeVisible();
    await expect(bar).toContainText('Itera Enterprise Workstation');
    await expect(bar.getByRole('button', { name: '최소화' })).toBeVisible();
    await expect(bar.getByRole('button', { name: '최대화' })).toBeVisible();
    await expect(bar.getByRole('button', { name: '닫기' })).toBeVisible();

    expect((await calls(page)).filter((c) => c.cmd === 'use_custom_chrome')).toEqual([
      { cmd: 'use_custom_chrome', args: { enabled: true } },
    ]);

    // the page below the bar is not covered by it
    const barBottom = (await bar.boundingBox())?.height ?? 0;
    const headerTop = (await page.locator('header').boundingBox())?.y ?? 0;
    expect(barBottom).toBe(36);
    expect(headerTop).toBeGreaterThanOrEqual(36);
  });

  test('창 버튼은 최소화·최대화·닫기를 셸에 요청한다', async ({ page }) => {
    await pretendToBeShell(page, 'windows');
    await login(page);
    const bar = titleBar(page);

    await bar.getByRole('button', { name: '최소화' }).click();
    await bar.getByRole('button', { name: '최대화' }).click();
    await bar.getByRole('button', { name: '닫기' }).click();

    await expect
      .poll(async () => (await calls(page)).map((c) => c.cmd))
      .toEqual(
        expect.arrayContaining(['plugin:window|minimize', 'plugin:window|toggle_maximize', 'plugin:window|close']),
      );
  });

  test('macOS 셸: 신호등 버튼 자리를 비우고 자체 창 버튼은 그리지 않는다', async ({ page }) => {
    await pretendToBeShell(page, 'macos');
    await login(page);

    const bar = titleBar(page);
    await expect(bar).toBeVisible();
    await expect(bar.getByRole('button')).toHaveCount(0);
    const text = await bar.getByText('Itera Enterprise Workstation').boundingBox();
    expect(text?.x ?? 0).toBeGreaterThanOrEqual(76);
    expect((await calls(page)).filter((c) => c.cmd === 'use_custom_chrome')).toHaveLength(0);
  });

  test('셸이 프레임 변경을 거부하면 제목 표시줄을 그리지 않는다(네이티브 프레임 유지)', async ({ page }) => {
    await pretendToBeShell(page, 'windows', true);
    await login(page);

    await expect(page.getByText('Itera 관리자').first()).toBeVisible();
    await expect(titleBar(page)).toHaveCount(0);
  });

  test('로그인 화면에서도 제목 표시줄이 있고, 로그인 후에도 같은 줄이 유지된다', async ({ page }) => {
    await pretendToBeShell(page, 'linux');
    await page.goto('/admin/login');
    await expect(titleBar(page)).toBeVisible();
    await expect(page.getByLabel('아이디')).toBeVisible();

    await login(page);
    await expect(titleBar(page)).toHaveCount(1);
    // one request to hide the frame in total: the bar is not remounted by the sign-in navigation
    expect((await calls(page)).filter((c) => c.cmd === 'use_custom_chrome' && c.args.enabled === true)).toHaveLength(1);
  });
});
