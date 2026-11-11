import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { logoutAction } from '@/features/auth/actions';
import { AdminShell } from '@/features/shell/AdminShell';
import { ADMIN_COOKIE } from '@/services/adminApi';

// Every page below needs a session. (The backend still verifies the token on each API call.)
export const dynamic = 'force-dynamic';

export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
  if (!(await cookies()).get(ADMIN_COOKIE)) redirect('/admin/login');
  return <AdminShell logoutAction={logoutAction}>{children}</AdminShell>;
}
