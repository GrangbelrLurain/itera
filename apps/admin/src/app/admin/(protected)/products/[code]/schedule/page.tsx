import { linkClass, Muted } from '@itera/ui';
import Link from 'next/link';
import { saveScheduleAction } from '@/features/products/actions';
import { loadProduct } from '@/features/products/loadProduct';
import { ScheduleForm } from '@/features/products/ProductEditors';
import { ProductHeader } from '@/features/products/ProductHeader';

export const dynamic = 'force-dynamic';

export default async function SchedulePage({ params }: { params: Promise<{ code: string }> }) {
  const { code: rawCode } = await params;
  const detail = await loadProduct(rawCode);
  const code = detail.product.code ?? '';
  return (
    <>
      <ProductHeader detail={detail} active="schedule" />
      <ScheduleForm action={saveScheduleAction.bind(null, code)} initial={detail.schedule} />
      <Muted as="p" className="mt-3">
        저장한 뒤{' '}
        <Link href={`/admin/products/${encodeURIComponent(code)}/departures`} className={linkClass}>
          출발일 관리
        </Link>
        에서 실제로 만들어지는 날짜를 확인할 수 있습니다.
      </Muted>
    </>
  );
}
