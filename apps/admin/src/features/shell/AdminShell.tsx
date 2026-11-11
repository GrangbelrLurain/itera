import { Button, navLinkClass } from '@itera/ui';
import Link from 'next/link';

const NAV = [
  { href: '/admin', label: '대시보드' },
  { href: '/admin/bookings', label: '예약 관리' },
  { href: '/admin/products', label: '상품 관리' },
  { href: '/admin/packages', label: '출발 조회' },
  { href: '/admin/channels', label: '채널 수수료' },
];

/** Admin frame: top bar and side menu. */
export function AdminShell({
  logoutAction,
  children,
}: {
  /** Server action that ends the session (owned by features/auth, passed in by the route layout). */
  logoutAction: () => Promise<void>;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-[calc(100vh-var(--shell-titlebar-h,0px))] bg-surface text-fg">
      <header className="bg-primary text-on-primary">
        <div className="flex items-center justify-between px-5 py-2.5 text-sm">
          <strong className="text-base">Itera 관리자</strong>
          <form action={logoutAction}>
            <Button
              type="submit"
              variant="outline"
              className="border-white/70 bg-transparent px-3 py-1 text-[13px] text-on-primary hover:bg-white/15"
            >
              로그아웃
            </Button>
          </form>
        </div>
      </header>
      <div className="flex min-h-[calc(100vh-var(--shell-titlebar-h,0px)-46px)] max-md:flex-col">
        <nav
          className="w-[200px] flex-none border-line border-r bg-surface-subtle py-4 max-md:flex max-md:w-auto max-md:overflow-x-auto max-md:border-r-0 max-md:border-b max-md:py-0"
          aria-label="관리자 메뉴"
        >
          {NAV.map((n) => (
            <Link key={n.href} href={n.href} className={navLinkClass}>
              {n.label}
            </Link>
          ))}
        </nav>
        <main className="min-w-0 flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
