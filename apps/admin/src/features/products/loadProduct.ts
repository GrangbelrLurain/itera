import { notFound } from 'next/navigation';
import { ApiError, adminApi, type ProductDetail } from '@/services/adminApi';

/** Loads a product for an edit page; unknown codes become a 404. (Other errors, including redirects, propagate.) */
export async function loadProduct(rawCode: string): Promise<ProductDetail> {
  try {
    return await adminApi.getProduct(decodeURIComponent(rawCode).toUpperCase());
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    throw e;
  }
}
