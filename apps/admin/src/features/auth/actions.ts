'use server';

import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { ADMIN_COOKIE, ApiError, adminLogin } from '@/services/adminApi';

function str(v: FormDataEntryValue | null): string {
  return typeof v === 'string' ? v.trim() : '';
}

function loginError(text: string): never {
  redirect(`/admin/login?error=${encodeURIComponent(text)}`);
}

export async function loginAction(formData: FormData) {
  const username = str(formData.get('username'));
  const password = typeof formData.get('password') === 'string' ? (formData.get('password') as string) : '';
  let expiresAt: string;
  let token: string;
  try {
    ({ token, expiresAt } = await adminLogin(username, password));
  } catch (e) {
    if (e instanceof ApiError) {
      loginError(e.status === 401 ? '아이디 또는 비밀번호가 올바르지 않습니다.' : e.message);
    }
    loginError('서버에 연결할 수 없습니다.');
  }
  (await cookies()).set(ADMIN_COOKIE, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.COOKIE_SECURE === 'true',
    path: '/',
    expires: new Date(expiresAt),
  });
  redirect('/admin');
}

export async function logoutAction() {
  (await cookies()).delete(ADMIN_COOKIE);
  redirect('/admin/login');
}
