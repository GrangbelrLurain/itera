'use server';

import { bpToPercent } from '@itera/domain/format';
import { parseCommissionRows, parseCostRows, parsePricingForm } from '@itera/domain/pricing';
import type { FormState } from '@itera/ui';
import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { submit } from '@/features/shell/form-actions';
import { ApiError, adminApi } from '@/services/adminApi';

export async function savePricingAction(code: string, _prev: FormState, formData: FormData): Promise<FormState> {
  return submit(
    parsePricingForm(formData),
    formData,
    (pricing) => adminApi.savePricing(code, pricing),
    (p) =>
      `원가 ${p.costs?.length ?? 0}건, 마진 ${bpToPercent(p.marginBp)}% + 고정 ${(p.marginFixed ?? 0).toLocaleString('ko-KR')}원을 저장했습니다. 원가가 입력된 구분은 판매가가 자동 계산됩니다.`,
  );
}

export async function saveCommissionsAction(_prev: FormState, formData: FormData): Promise<FormState> {
  return submit(
    parseCommissionRows(formData),
    formData,
    (rows) => adminApi.replaceCommissions(rows),
    (rows) => `수수료 ${rows.length}건을 저장했습니다. 이미 만든 예약의 수수료는 바뀌지 않습니다.`,
  );
}

// ---- date-specific cost overrides -------------------------------------------------------------

export async function saveCostOverridesAction(
  code: string,
  date: string,
  _prev: FormState,
  formData: FormData,
): Promise<FormState> {
  return submit(
    parseCostRows(formData, 'costs'),
    formData,
    (rows) => adminApi.saveCostOverrides(code, date, rows),
    (rows) =>
      rows.length === 0
        ? `${date}의 원가 조정을 모두 지웠습니다. 상품 기본 원가로 돌아갑니다.`
        : `${date}의 원가 조정 ${rows.length}건을 저장했습니다. 이 날짜의 새 예약부터 적용됩니다.`,
  );
}

export async function clearCostOverridesAction(code: string, date: string) {
  const base = `/admin/products/${encodeURIComponent(code)}/departures/${date}`;
  try {
    await adminApi.saveCostOverrides(code, date, []);
  } catch (e) {
    if (e instanceof ApiError) redirect(`${base}?error=${encodeURIComponent(e.message)}`);
    throw e;
  }
  revalidatePath('/admin', 'layout');
  redirect(`${base}?msg=${encodeURIComponent('원가 조정을 모두 지웠습니다. 상품 기본 원가로 돌아갑니다.')}`);
}
