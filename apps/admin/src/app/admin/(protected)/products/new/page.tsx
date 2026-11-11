import { backLinkClass, Muted, PageTitle } from '@itera/ui';
import Link from 'next/link';
import { createProductAction } from '@/features/products/actions';
import { ProductForm } from '@/features/products/ProductForm';

export const dynamic = 'force-dynamic';

export default function NewProductPage() {
  return (
    <>
      <Link href="/admin/products" className={backLinkClass}>
        ← 상품 목록
      </Link>
      <PageTitle>상품 등록</PageTitle>
      <Muted as="p">기본 정보와 가격을 먼저 등록한 뒤, 일정과 출발 규칙은 저장 후 이어지는 화면에서 입력합니다.</Muted>
      <ProductForm action={createProductAction} initial={null} mode="create" />
    </>
  );
}
