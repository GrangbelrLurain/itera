'use server';

import { optStr, str } from '@itera/domain/forms';
import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { ApiError, adminApi } from '@/services/adminApi';

// Each button of the departure calendar is its own tiny form; the result comes back as a banner message.

function departuresUrl(code: string, key: 'msg' | 'error', text: string, from?: string | null): string {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  params.set(key, text);
  return `/admin/products/${encodeURIComponent(code)}/departures?${params.toString()}`;
}

async function departureAction(
  code: string,
  formData: FormData,
  run: () => Promise<unknown>,
  okMessage: string,
): Promise<never> {
  const from = optStr(formData, 'from');
  try {
    await run();
  } catch (e) {
    if (e instanceof ApiError) redirect(departuresUrl(code, 'error', e.message, from));
    throw e;
  }
  revalidatePath('/admin', 'layout');
  redirect(departuresUrl(code, 'msg', okMessage, from));
}

export async function closeDepartureAction(code: string, date: string, formData: FormData) {
  await departureAction(code, formData, () => adminApi.closeDeparture(code, date), `${date} 출발을 마감했습니다.`);
}

export async function reopenDepartureAction(code: string, date: string, formData: FormData) {
  await departureAction(code, formData, () => adminApi.reopenDeparture(code, date), `${date} 출발을 다시 열었습니다.`);
}

export async function cancelDepartureAction(code: string, date: string, formData: FormData) {
  await departureAction(code, formData, () => adminApi.cancelDeparture(code, date), `${date} 출발을 취소했습니다.`);
}

export async function setCapacityAction(code: string, date: string, formData: FormData) {
  const capacity = Number(str(formData, 'capacity'));
  await departureAction(
    code,
    formData,
    () => adminApi.setDepartureCapacity(code, date, capacity),
    `${date} 정원을 ${capacity}석으로 바꿨습니다.`,
  );
}

export async function addDepartureAction(code: string, formData: FormData) {
  const date = str(formData, 'date');
  await departureAction(code, formData, () => adminApi.addDeparture(code, date), `${date} 출발을 추가했습니다.`);
}
