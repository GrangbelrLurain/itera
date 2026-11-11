import { apiUrl, expect, successBanner, test } from '../support/fixtures';

test.describe('원가 · 가격', () => {
  test('날짜별 원가 조정은 그 날짜의 가격만 바꾸고, 지우면 원래대로 돌아간다', async ({
    admin,
    request,
    firstDeparture,
  }) => {
    const date = await firstDeparture('DAD501');
    const price = async () => {
      const res = await request.get(`${apiUrl}/api/v1/packages/DAD501/departures/${date}`);
      return (await res.json()).prices.find((p: { targetType: string }) => p.targetType === 'ADULT').salePrice;
    };
    // cost 520,000 + 300,000 + 150,000 + 60,000 = 1,030,000 and a 25 % margin
    expect(await price()).toBe(1_287_500);

    await admin.goto(`/admin/products/DAD501/departures/${date}`);
    await admin.getByRole('button', { name: '+ 원가 조정 추가' }).click();
    // the new row starts as HOTEL / ADULT: replace the hotel cost 300,000 with 450,000
    await admin.getByLabel('이 날짜의 원가 (1인, 원)').fill('450000');
    await admin.getByRole('button', { name: '원가 조정 저장' }).click();

    await expect(successBanner(admin)).toContainText('원가 조정 1건을 저장했습니다');
    await expect(admin.getByRole('row', { name: /성인.*조정됨/ })).toContainText('1,475,000원');
    expect(await price()).toBe(1_475_000); // (1,030,000 - 300,000 + 450,000) * 1.25

    // another date keeps the product's cost
    const other = new Date(`${date}T00:00:00Z`);
    other.setUTCDate(other.getUTCDate() + 7);
    const otherRes = await request.get(
      `${apiUrl}/api/v1/packages/DAD501/departures/${other.toISOString().slice(0, 10)}`,
    );
    expect((await otherRes.json()).prices[0].salePrice).toBe(1_287_500);

    admin.once('dialog', (d) => d.accept());
    await admin.getByRole('button', { name: '조정 모두 지우기' }).click();
    await expect(successBanner(admin)).toContainText('원가 조정을 모두 지웠습니다');
    await expect(successBanner(admin)).toHaveCount(1); // no leftover banner of the earlier save
    await expect(admin.getByText('이 날짜는 상품 기본 원가를 그대로 씁니다.')).toBeVisible(); // rows are gone
    expect(await price()).toBe(1_287_500);
  });

  test('마진율을 바꾸면 미리보기와 공개 가격이 함께 바뀐다', async ({ admin, request, firstDeparture }) => {
    const date = await firstDeparture('DAD501');
    await admin.goto('/admin/products/DAD501/pricing');

    await admin.getByLabel('마진율 (%, 원가 대비)').fill('30');
    await admin.getByRole('button', { name: '원가 · 마진 저장' }).click();
    await expect(successBanner(admin)).toContainText('마진 30%');

    const res = await request.get(`${apiUrl}/api/v1/packages/DAD501/departures/${date}`);
    expect((await res.json()).prices[0].salePrice).toBe(1_339_000); // 1,030,000 * 1.30
  });

  test('잘못된 마진율은 저장하지 않고 입력값을 그대로 둔다', async ({ admin }) => {
    await admin.goto('/admin/products/DAD501/pricing');
    const margin = admin.getByLabel('마진율 (%, 원가 대비)');
    await margin.evaluate((el: HTMLInputElement) => el.removeAttribute('min'));
    await margin.fill('-5');
    await admin.getByRole('button', { name: '원가 · 마진 저장' }).click();

    await expect(admin.getByText('마진율은 0 이상의 숫자여야 합니다.')).toBeVisible();
    await expect(margin).toHaveValue('-5');
  });
});

test.describe('채널 수수료', () => {
  test('수수료율을 고쳐 저장하면 다시 열어도 유지된다', async ({ admin }) => {
    await admin.goto('/admin/channels');
    const rate = admin.getByLabel('수수료율 (%)').first();
    await expect(rate).toHaveValue('8');

    await rate.fill('12.5');
    await admin.getByRole('button', { name: '수수료 저장' }).click();
    await expect(successBanner(admin)).toContainText('수수료 3건을 저장했습니다');

    await admin.reload();
    await expect(admin.getByLabel('수수료율 (%)').first()).toHaveValue('12.5');
  });

  test('행을 추가하고 삭제할 수 있다', async ({ admin }) => {
    await admin.goto('/admin/channels');
    await admin.getByRole('button', { name: '+ 수수료 행 추가' }).click();
    await admin.getByLabel('채널 코드').last().fill('NEW_CH');
    await admin.getByLabel('수수료율 (%)').last().fill('5');
    await admin.getByRole('button', { name: '수수료 저장' }).click();
    await expect(successBanner(admin)).toContainText('수수료 4건을 저장했습니다');

    await admin.reload();
    await admin.getByRole('button', { name: '삭제' }).last().click();
    await admin.getByRole('button', { name: '수수료 저장' }).click();
    await expect(successBanner(admin)).toContainText('수수료 3건을 저장했습니다');
  });
});
