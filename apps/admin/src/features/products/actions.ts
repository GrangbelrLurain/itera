'use server';

import { formValues } from '@itera/domain/forms';
import { parseExtrasForm, parseItineraryRows, parseProductForm, parseScheduleForm } from '@itera/domain/product';
import type { FormState } from '@itera/ui';
import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { failed, invalid, submit } from '@/features/shell/form-actions';
import { ApiError, adminApi } from '@/services/adminApi';

export async function createProductAction(_prev: FormState, formData: FormData): Promise<FormState> {
  const parsed = parseProductForm(formData);
  if (!parsed.ok) return invalid(parsed.error, formData);
  let code: string;
  try {
    const created = await adminApi.createProduct(parsed.value);
    code = created.product.code ?? '';
  } catch (e) {
    return failed(e, formData);
  }
  revalidatePath('/admin/products');
  redirect(`/admin/products/${encodeURIComponent(code)}?created=1`);
}

export async function updateProductAction(code: string, _prev: FormState, formData: FormData): Promise<FormState> {
  const parsed = parseProductForm(formData);
  if (!parsed.ok) return invalid(parsed.error, formData);
  try {
    await adminApi.updateProduct(code, { ...parsed.value, code });
  } catch (e) {
    return failed(e, formData);
  }
  revalidatePath('/admin/products', 'layout');
  return { message: '저장했습니다.', values: formValues(formData) };
}

export async function setActiveAction(code: string, active: boolean) {
  try {
    await adminApi.setProductActive(code, active);
  } catch (e) {
    if (e instanceof ApiError)
      redirect(`/admin/products/${encodeURIComponent(code)}?error=${encodeURIComponent(e.message)}`);
    throw e;
  }
  revalidatePath('/admin/products', 'layout');
  redirect(
    `/admin/products/${encodeURIComponent(code)}?msg=${encodeURIComponent(active ? '상품을 활성화했습니다.' : '상품을 비활성화했습니다. 기존 예약은 유지되고 새 예약만 막힙니다.')}`,
  );
}

export async function saveItineraryAction(code: string, _prev: FormState, formData: FormData): Promise<FormState> {
  return submit(
    parseItineraryRows(formData),
    formData,
    (rows) => adminApi.saveItinerary(code, rows),
    (rows) => `일정 ${rows.length}건을 저장했습니다.`,
  );
}

export async function saveScheduleAction(code: string, _prev: FormState, formData: FormData): Promise<FormState> {
  return submit(
    parseScheduleForm(formData),
    formData,
    (schedule) => adminApi.saveSchedule(code, schedule),
    (s) =>
      `출발 규칙 ${s.rules?.length ?? 0}건, 제외일 ${s.exclusions?.length ?? 0}건을 저장했습니다. 이미 예약이 있는 날짜는 규칙과 상관없이 유지됩니다.`,
  );
}

export async function saveExtrasAction(code: string, _prev: FormState, formData: FormData): Promise<FormState> {
  return submit(
    parseExtrasForm(formData),
    formData,
    (extras) => adminApi.saveExtras(code, extras),
    () => '부가 정보를 저장했습니다.',
  );
}
