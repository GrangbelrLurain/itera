import { bpToPercent, won } from '@itera/domain/format';
import { TARGET_LABELS, TARGET_TYPES } from '@itera/domain/pricing';
import { Badge, Button, Card, Field, linkClass, Muted, Select, Table } from '@itera/ui';
import Link from 'next/link';
import { savePricingAction } from '@/features/pricing/actions';
import { PricingForm } from '@/features/pricing/PricingForm';
import { loadProduct } from '@/features/products/loadProduct';
import { ProductHeader } from '@/features/products/ProductHeader';
import { first, LoadError } from '@/features/shell/page-parts';
import { ApiError, adminApi, type PricingPreview } from '@/services/adminApi';

export const dynamic = 'force-dynamic';

function signed(v: number | null | undefined) {
  if (v === null || v === undefined) return '-';
  return <span className={v < 0 ? 'font-bold text-danger' : undefined}>{won(v)}</span>;
}

const money = (v: number | null | undefined) => (v === null || v === undefined ? '-' : won(v));

export default async function PricingPage({
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
  const channel = first(sp.channel) ?? 'DIRECT';

  let pricing: Awaited<ReturnType<typeof adminApi.getPricing>>;
  let preview: PricingPreview;
  let channels: string[];
  try {
    pricing = await adminApi.getPricing(code);
    preview = await adminApi.previewPricing(code, channel);
    const commissions = await adminApi.listCommissions();
    channels = ['DIRECT', ...Array.from(new Set(commissions.map((c) => c.channelCode))).filter((c) => c !== 'DIRECT')];
  } catch (e) {
    if (e instanceof ApiError) {
      return (
        <>
          <ProductHeader detail={detail} active="pricing" />
          <LoadError message={e.message} />
        </>
      );
    }
    throw e;
  }

  const targets = TARGET_TYPES.filter((t) => detail.product.prices.some((p) => p.targetType === t));
  const rows = [...preview.rows].sort(
    (a, b) => TARGET_TYPES.indexOf(a.targetType) - TARGET_TYPES.indexOf(b.targetType),
  );

  return (
    <>
      <ProductHeader detail={detail} active="pricing" />

      <PricingForm
        action={savePricingAction.bind(null, code)}
        costs={pricing.costs}
        marginBp={pricing.marginBp}
        marginFixed={pricing.marginFixed}
        targets={targets}
      />

      <div className="mt-6">
        <Card title="가격 미리보기 (저장된 값 기준)">
          <form method="get" className="flex flex-wrap items-center gap-2">
            <Field label="판매 채널" className="flex-row items-center gap-2">
              <Select name="channel" defaultValue={preview.channelCode} className="w-auto">
                {channels.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </Select>
            </Field>
            <Button type="submit" variant="outline">
              보기
            </Button>
            <Muted>
              {preview.commissionBp !== null && preview.commissionBp !== undefined
                ? `적용 수수료: ${bpToPercent(preview.commissionBp)}%${preview.commissionFixed ? ` + ${won(preview.commissionFixed)}` : ''}`
                : preview.commissionFixed
                  ? `적용 수수료: 1인 ${won(preview.commissionFixed)}`
                  : '이 채널에는 수수료가 없습니다.'}{' '}
              <Link href="/admin/channels" className={linkClass}>
                수수료 편집
              </Link>
            </Muted>
          </form>
          <Table>
            <thead>
              <tr>
                <th>구분</th>
                <th>방식</th>
                <th className="text-right">원가</th>
                <th className="text-right">마진</th>
                <th className="text-right">판매가</th>
                <th className="text-right">할인</th>
                <th className="text-right">통과(유류·세금)</th>
                <th className="text-right">고객 결제액</th>
                <th className="text-right">수수료</th>
                <th className="text-right">순수익</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.targetType}>
                  <td>
                    {TARGET_LABELS[r.targetType]}
                    {!r.salesAvailable && <Muted> (판매 안 함)</Muted>}
                  </td>
                  <td>
                    <Badge tone={r.mode === 'COST' ? 'success' : 'neutral'}>
                      {r.mode === 'COST' ? '원가 기반' : '수동'}
                    </Badge>
                  </td>
                  <td className="text-right tabular-nums">{money(r.cost)}</td>
                  <td className="text-right tabular-nums">{money(r.margin)}</td>
                  <td className="text-right tabular-nums">{won(r.salePrice)}</td>
                  <td className="text-right tabular-nums">{r.discount > 0 ? `-${won(r.discount)}` : '-'}</td>
                  <td className="text-right tabular-nums">{won(r.passThrough)}</td>
                  <td className="text-right tabular-nums">
                    <strong>{won(r.payable)}</strong>
                  </td>
                  <td className="text-right tabular-nums">{r.commission > 0 ? won(r.commission) : '-'}</td>
                  <td className="text-right tabular-nums">{signed(r.profit)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Muted as="p">
            순수익 = 할인 후 판매가 − 원가 − 수수료. 할인이나 수수료가 마진보다 크면 음수(빨간색)로 표시됩니다. 수동
            가격은 원가를 알 수 없어 순수익을 계산하지 않습니다.
          </Muted>
        </Card>
      </div>
    </>
  );
}
