import { Banner, Button, Field, Input } from '@itera/ui';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { loginAction } from '@/features/auth/actions';
import { ADMIN_COOKIE } from '@/services/adminApi';

export const dynamic = 'force-dynamic';

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const sp = await searchParams;
  const error = typeof sp.error === 'string' ? sp.error : null;
  const expired = sp.expired !== undefined;
  // A public demo explains itself here (which account to use, when the data resets); unset everywhere else.
  const demoNotice = process.env.DEMO_NOTICE?.trim() || null;
  // Already signed in (and the cookie is still there): go straight to the dashboard.
  if (!expired && !error && (await cookies()).get(ADMIN_COOKIE)) redirect('/admin');

  return (
    <div className="flex min-h-[calc(100vh-var(--shell-titlebar-h,0px))] items-center justify-center bg-surface-strip p-4">
      <div className="w-full max-w-[380px] overflow-hidden rounded-lg border border-line bg-surface">
        <header className="bg-primary px-6 py-[18px] font-bold text-lg text-on-primary">Itera 관리자</header>
        <form action={loginAction} className="flex flex-col gap-3.5 p-6">
          {demoNotice && <Banner kind="success">{demoNotice}</Banner>}
          {expired && <Banner kind="error">세션이 만료되었습니다. 다시 로그인하세요.</Banner>}
          {error && <Banner kind="error">{error}</Banner>}
          <Field label="아이디">
            <Input name="username" autoComplete="username" required autoFocus />
          </Field>
          <Field label="비밀번호">
            <Input name="password" type="password" autoComplete="current-password" required />
          </Field>
          <Button type="submit" block>
            로그인
          </Button>
        </form>
      </div>
    </div>
  );
}
