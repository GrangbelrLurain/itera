import { saveItineraryAction } from '@/features/products/actions';
import { loadProduct } from '@/features/products/loadProduct';
import { ItineraryForm } from '@/features/products/ProductEditors';
import { ProductHeader } from '@/features/products/ProductHeader';

export const dynamic = 'force-dynamic';

export default async function ItineraryPage({ params }: { params: Promise<{ code: string }> }) {
  const { code: rawCode } = await params;
  const detail = await loadProduct(rawCode);
  const code = detail.product.code ?? '';
  return (
    <>
      <ProductHeader detail={detail} active="itinerary" />
      <ItineraryForm
        action={saveItineraryAction.bind(null, code)}
        initial={detail.itinerary}
        days={detail.product.days}
      />
    </>
  );
}
