import { saveExtrasAction } from '@/features/products/actions';
import { loadProduct } from '@/features/products/loadProduct';
import { ExtrasForm } from '@/features/products/ProductEditors';
import { ProductHeader } from '@/features/products/ProductHeader';

export const dynamic = 'force-dynamic';

export default async function ExtrasPage({ params }: { params: Promise<{ code: string }> }) {
  const { code: rawCode } = await params;
  const detail = await loadProduct(rawCode);
  const code = detail.product.code ?? '';
  return (
    <>
      <ProductHeader detail={detail} active="extras" />
      <ExtrasForm
        action={saveExtrasAction.bind(null, code)}
        tours={detail.extras.optionalTours ?? []}
        shoppings={detail.extras.shoppings ?? []}
        remark={detail.extras.remark}
        condition={detail.extras.condition}
      />
    </>
  );
}
