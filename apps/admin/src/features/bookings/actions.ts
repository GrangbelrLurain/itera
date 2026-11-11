'use server';

import { str } from '@itera/domain/forms';
import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { ApiError, adminApi } from '@/services/adminApi';

function back(path: string, key: 'msg' | 'error', text: string): never {
  redirect(`${path}?${key}=${encodeURIComponent(text)}`);
}

export async function confirmPaymentAction(code: string, formData: FormData) {
  const path = `/admin/bookings/${encodeURIComponent(code)}`;
  try {
    await adminApi.confirmPayment(code, str(formData, 'paymentReference'));
  } catch (e) {
    if (e instanceof ApiError) back(path, 'error', e.message);
    throw e; // includes Next.js redirects (expired session)
  }
  revalidatePath('/admin', 'layout');
  back(path, 'msg', '결제를 확인했습니다. 예약이 확정되었습니다.');
}

export async function cancelBookingAction(code: string, formData: FormData) {
  const path = `/admin/bookings/${encodeURIComponent(code)}`;
  try {
    await adminApi.cancelBooking(code, str(formData, 'reason') || undefined);
  } catch (e) {
    if (e instanceof ApiError) back(path, 'error', e.message);
    throw e;
  }
  revalidatePath('/admin', 'layout');
  back(path, 'msg', '예약을 취소했습니다.');
}
