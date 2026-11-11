import { Banner, Card, ConfirmSubmit, linkClass, Muted } from '@itera/ui';
import Link from 'next/link';
import { setActiveAction, updateProductAction } from '@/features/products/actions';
import { loadProduct } from '@/features/products/loadProduct';
import { ProductForm } from '@/features/products/ProductForm';
import { ProductHeader } from '@/features/products/ProductHeader';
import { first, Notices } from '@/features/shell/page-parts';

export const dynamic = 'force-dynamic';

export default async function ProductBasicPage({
  params,
  searchParams,
}: {
  params: Promise<{ code: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { code: rawCode } = await params;
  const sp = await searchParams;
  const detail = await loadProduct(rawCode);
  const code = detail.product.code ?? '';
  const active = detail.product.active !== false;
  const base = `/admin/products/${encodeURIComponent(code)}`;

  return (
    <>
      <ProductHeader detail={detail} active="basic" />

      {first(sp.created) && (
        <Banner kind="success">
          상품을 등록했습니다. 다음으로{' '}
          <Link href={`${base}/schedule`} className={linkClass}>
            출발 규칙
          </Link>
          과{' '}
          <Link href={`${base}/itinerary`} className={linkClass}>
            일정
          </Link>
          을 입력하세요. 출발 규칙이 없으면 출발일이 만들어지지 않습니다.
        </Banner>
      )}
      <Notices msg={first(sp.msg)} error={first(sp.error)} />

      <ProductForm action={updateProductAction.bind(null, code)} initial={detail.product} mode="edit" />

      <div className="mt-6">
        <Card title="판매 상태">
          <Muted as="p">
            비활성 상품은 목록에서 빠지고 새 예약을 받지 않습니다. 이미 있는 예약과 저장된 출발일은 그대로 유지됩니다.
          </Muted>
          <form action={setActiveAction.bind(null, code, !active)}>
            <ConfirmSubmit
              variant={active ? 'danger' : 'primary'}
              message={active ? '이 상품을 비활성화할까요? 새 예약이 막힙니다.' : '이 상품을 다시 활성화할까요?'}
            >
              {active ? '상품 비활성화' : '상품 활성화'}
            </ConfirmSubmit>
          </form>
        </Card>
      </div>
    </>
  );
}
