import { Badge, backLinkClass, Muted, PageTitle, tabClass } from '@itera/ui';
import Link from 'next/link';
import type { ProductDetail } from '@/services/adminApi';

const TABS = [
  { key: 'basic', label: '기본 · 가격', path: '' },
  { key: 'pricing', label: '원가 · 마진', path: '/pricing' },
  { key: 'itinerary', label: '일정', path: '/itinerary' },
  { key: 'schedule', label: '출발 규칙', path: '/schedule' },
  { key: 'departures', label: '출발일 관리', path: '/departures' },
  { key: 'extras', label: '부가 정보', path: '/extras' },
] as const;

export type ProductTab = (typeof TABS)[number]['key'];

/** Title and tab bar shared by the product edit pages. */
export function ProductHeader({ detail, active }: { detail: ProductDetail; active: ProductTab }) {
  const p = detail.product;
  const base = `/admin/products/${encodeURIComponent(p.code ?? '')}`;
  return (
    <>
      <Link href="/admin/products" className={backLinkClass}>
        ← 상품 목록
      </Link>
      <PageTitle>
        {p.name} <Muted>({p.code})</Muted> {p.active === false && <Badge tone="muted">비활성</Badge>}
      </PageTitle>
      <nav className="mt-3 mb-4 flex gap-1 overflow-x-auto border-line border-b" aria-label="상품 편집 메뉴">
        {TABS.map((t) => (
          <Link key={t.key} href={`${base}${t.path}`} className={tabClass(t.key === active)}>
            {t.label}
          </Link>
        ))}
      </nav>
    </>
  );
}
