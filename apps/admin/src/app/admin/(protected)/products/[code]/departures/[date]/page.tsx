import { won } from '@itera/domain/format';
import { COST_COMPONENT_LABELS, TARGET_LABELS, TARGET_TYPES } from '@itera/domain/pricing';
import { Badge, Button, backLinkClass, Card, ConfirmSubmit, Field, linkClass, Muted, Select, Table } from '@itera/ui';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { clearCostOverridesAction, saveCostOverridesAction } from '@/features/pricing/actions';
import { CostOverrideForm } from '@/features/pricing/PricingForm';
import { loadProduct } from '@/features/products/loadProduct';
import { ProductHeader } from '@/features/products/ProductHeader';
import { first, LoadError, Notices } from '@/features/shell/page-parts';
import { ApiError, adminApi, type CostOverrideView, type PricingPreview } from '@/services/adminApi';
import { departureHref } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

function signed(v: number | null | undefined) {
  if (v === null || v === undefined) return '-';
  return <span className={v < 0 ? 'font-bold text-danger' : undefined}>{won(v)}</span>;
}

const money = (v: number | null | undefined) => (v === null || v === undefined ? '-' : won(v));

export default async function DepartureCostPage({
  params,
  searchParams,
}: {
  params: Promise<{ code: string; date: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { code: rawCode, date } = await params;
  const sp = await searchParams;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) notFound();
  const detail = await loadProduct(rawCode);
  const code = detail.product.code ?? '';
  const channel = first(sp.channel) ?? 'DIRECT';

  let view: CostOverrideView;
  let preview: PricingPreview;
  let channels: string[];
  try {
    view = await adminApi.getCostOverrides(code, date);
    preview = await adminApi.previewPricing(code, channel, date);
    const commissions = await adminApi.listCommissions();
    channels = ['DIRECT', ...Array.from(new Set(commissions.map((c) => c.channelCode))).filter((c) => c !== 'DIRECT')];
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 404) notFound();
      return (
        <>
          <ProductHeader detail={detail} active="departures" />
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
  const back = `/admin/products/${encodeURIComponent(code)}/departures?from=${date}`;

  return (
    <>
      <ProductHeader detail={detail} active="departures" />
      <Link href={back} className={backLinkClass}>
        ← 출발일 관리
      </Link>
      <h2 className="mb-1 font-bold text-lg">
        {date} 원가 조정{' '}
        <Link href={departureHref(code, date)} className={`${linkClass} font-normal text-sm`}>
          출발 상세 보기
        </Link>
      </h2>
      <Muted as="p">
        성수기·특가처럼 이 출발일만 원가가 다를 때 사용합니다. 상품 원가와 <strong>같은 항목·대상</strong>이면 그 원가를
        대체하고, 없는 항목이면 이 날짜에만 원가 줄이 추가됩니다. 조정은 이 날짜의 <strong>새 예약부터</strong> 적용되며
        이미 만든 예약은 바뀌지 않습니다.
      </Muted>

      <Notices msg={first(sp.msg)} error={first(sp.error)} />

      <Card title="상품 기본 원가 (참고)">
        {view.base.length === 0 ? (
          <Muted as="p">
            상품에 원가 구성이 없습니다. 이 날짜에 원가 줄을 추가하면 그 구분은 이 날짜만 원가 기반이 됩니다.
          </Muted>
        ) : (
          <Table>
            <thead>
              <tr>
                <th>대상</th>
                <th>항목</th>
                <th className="text-right">원가</th>
                <th>메모</th>
              </tr>
            </thead>
            <tbody>
              {view.base.map((c, i) => (
                // biome-ignore lint/suspicious/noArrayIndexKey: read-only list, rows have no id
                <tr key={i}>
                  <td>{TARGET_LABELS[c.targetType]}</td>
                  <td>{COST_COMPONENT_LABELS[c.componentType]}</td>
                  <td className="text-right tabular-nums">{won(c.cost)}</td>
                  <td className="text-[13px] text-fg-muted">{c.note ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <Card title="이 날짜의 원가 조정">
        <CostOverrideForm
          // a redirect back to this page ("clear all") must not keep the old form state: remount on the message
          key={first(sp.msg) ?? 'form'}
          action={saveCostOverridesAction.bind(null, code, date)}
          overrides={view.overrides}
          targets={targets}
        />
        {view.overrides.length > 0 && (
          <form action={clearCostOverridesAction.bind(null, code, date)}>
            <ConfirmSubmit message="이 날짜의 원가 조정을 모두 지우고 상품 기본 원가로 되돌릴까요?">
              조정 모두 지우기
            </ConfirmSubmit>
          </form>
        )}
      </Card>

      <Card title="이 날짜의 가격 미리보기 (저장된 값 기준)">
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
        </form>
        <Table>
          <thead>
            <tr>
              <th>구분</th>
              <th className="text-right">원가</th>
              <th className="text-right">마진</th>
              <th className="text-right">판매가</th>
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
                  {r.costOverridden && (
                    <Badge tone="warning" style={{ marginLeft: 6 }}>
                      조정됨
                    </Badge>
                  )}
                  {r.mode === 'MANUAL' && <Muted> (수동)</Muted>}
                </td>
                <td className="text-right tabular-nums">{money(r.cost)}</td>
                <td className="text-right tabular-nums">{money(r.margin)}</td>
                <td className="text-right tabular-nums">{won(r.salePrice)}</td>
                <td className="text-right tabular-nums">
                  <strong>{won(r.payable)}</strong>
                </td>
                <td className="text-right tabular-nums">{r.commission > 0 ? won(r.commission) : '-'}</td>
                <td className="text-right tabular-nums">{signed(r.profit)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Card>
    </>
  );
}
