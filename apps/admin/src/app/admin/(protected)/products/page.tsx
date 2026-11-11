import { Badge, buttonVariants, Card, Muted, PageTitle, Table } from '@itera/ui';
import Link from 'next/link';
import { LoadError } from '@/features/shell/page-parts';
import { ApiError, adminApi, type ProductSummary } from '@/services/adminApi';

export const dynamic = 'force-dynamic';

export default async function ProductListPage() {
  let products: ProductSummary[];
  try {
    products = await adminApi.listProducts();
  } catch (e) {
    if (e instanceof ApiError) return <LoadError message={e.message} />;
    throw e;
  }

  return (
    <>
      <PageTitle>상품 관리</PageTitle>
      <Muted as="p">
        모상품과 출발 규칙을 등록합니다. 출발일은 규칙에서 계산되며, 예약이 생기거나 관리자가 조치한 날짜만 저장됩니다.
      </Muted>
      <div className="mt-3 mb-3">
        <Link href="/admin/products/new" className={buttonVariants()}>
          + 상품 등록
        </Link>
      </div>

      <p className="mt-1 mb-2 text-sm">
        총 <strong>{products.length}</strong>개
      </p>
      <Card className="p-0">
        <Table>
          <thead>
            <tr>
              <th>상품</th>
              <th>일정</th>
              <th>출발 규칙</th>
              <th className="text-right">기본 정원</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.code}>
                <td>
                  <Link href={`/admin/products/${encodeURIComponent(p.code)}`}>{p.name}</Link>
                  <Muted as="div">
                    {p.code}
                    {p.grade ? ` · ${p.grade}` : ''}
                  </Muted>
                </td>
                <td>
                  {p.nights}박 {p.days}일
                </td>
                <td>{p.scheduleSummary}</td>
                <td className="text-right tabular-nums">{p.defaultCapacity}</td>
                <td>
                  <Badge tone={p.active ? 'success' : 'muted'}>{p.active ? '활성' : '비활성'}</Badge>
                </td>
              </tr>
            ))}
            {products.length === 0 && (
              <tr>
                <td colSpan={5} className="text-[13px] text-fg-muted">
                  등록된 상품이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </Table>
      </Card>
    </>
  );
}
