import { Muted, PageTitle } from '@itera/ui';
import { saveCommissionsAction } from '@/features/pricing/actions';
import { CommissionForm } from '@/features/pricing/PricingForm';
import { LoadError } from '@/features/shell/page-parts';
import { ApiError, adminApi, type CommissionInput } from '@/services/adminApi';

export const dynamic = 'force-dynamic';

export default async function ChannelsPage() {
  let rows: CommissionInput[];
  try {
    rows = await adminApi.listCommissions();
  } catch (e) {
    if (e instanceof ApiError) return <LoadError message={e.message} />;
    throw e;
  }
  return (
    <>
      <PageTitle>채널 수수료</PageTitle>
      <Muted as="p">판매 채널(직접 판매, 대리점 등)별로 지급할 수수료를 관리합니다.</Muted>
      <CommissionForm action={saveCommissionsAction} rows={rows} />
    </>
  );
}
