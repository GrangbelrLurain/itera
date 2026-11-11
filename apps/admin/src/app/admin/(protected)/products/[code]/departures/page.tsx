import {
  addMonths,
  DEPARTURE_STATUS_LABELS,
  DEPARTURE_STATUS_TONES,
  previousWindowStart,
  WEEKDAY_LABELS,
} from '@itera/domain/departure';
import { Badge, Button, Card, ConfirmSubmit, Input, Muted, Pager, PagerOff, pagerLinkClass, Table } from '@itera/ui';
import Link from 'next/link';
import {
  addDepartureAction,
  cancelDepartureAction,
  closeDepartureAction,
  reopenDepartureAction,
  setCapacityAction,
} from '@/features/departures/actions';
import { loadProduct } from '@/features/products/loadProduct';
import { ProductHeader } from '@/features/products/ProductHeader';
import { first, LoadError, Notices } from '@/features/shell/page-parts';
import { ApiError, adminApi, type DepartureItem } from '@/services/adminApi';
import { departureHref } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

const WINDOW_MONTHS = 3;

export default async function DeparturesPage({
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
  const today = new Date().toISOString().slice(0, 10);
  const fromParam = first(sp.from);
  const from = fromParam && /^\d{4}-\d{2}-\d{2}$/.test(fromParam) ? fromParam : today;
  const to = addMonths(from, WINDOW_MONTHS);

  let items: DepartureItem[];
  try {
    items = await adminApi.listDepartures(code, { from, to });
  } catch (e) {
    if (e instanceof ApiError) {
      return (
        <>
          <ProductHeader detail={detail} active="departures" />
          <LoadError message={e.message} />
        </>
      );
    }
    throw e;
  }

  const prevFrom = previousWindowStart(from, today, WINDOW_MONTHS);
  const link = (f: string) => `/admin/products/${encodeURIComponent(code)}/departures?from=${f}`;

  return (
    <>
      <ProductHeader detail={detail} active="departures" />
      <Notices msg={first(sp.msg)} error={first(sp.error)} />

      <Card title="출발일 추가 (규칙 밖의 단일 날짜)">
        <form action={addDepartureAction.bind(null, code)} className="flex flex-wrap items-center gap-2">
          <input type="hidden" name="from" value={from} />
          <Input type="date" name="date" min={today} required className="w-auto min-w-[220px]" />
          <Button type="submit">추가</Button>
          <Muted>추가한 날짜는 규칙을 바꿔도 유지됩니다.</Muted>
        </form>
      </Card>

      <Muted as="p" className="mb-2">
        {from} ~ {to} · 규칙에서 계산된 날짜와 저장된 날짜를 함께 보여줍니다. 마감·정원·취소 조치를 하면 그 날짜가
        저장됩니다.
      </Muted>
      <Card className="p-0">
        <Table>
          <thead>
            <tr>
              <th>출발일</th>
              <th>상태</th>
              <th className="text-right">정원</th>
              <th className="text-right">예약</th>
              <th className="text-right">잔여</th>
              <th>구분</th>
              <th>원가</th>
              <th>조치</th>
            </tr>
          </thead>
          <tbody>
            {items.map((d) => {
              const canceled = d.status === 'CANCELED';
              const closed = d.status === 'CLOSED';
              return (
                <tr key={d.date}>
                  <td>
                    {canceled ? d.date : <Link href={departureHref(code, d.date)}>{d.date}</Link>}{' '}
                    <Muted>({WEEKDAY_LABELS[d.weekday]})</Muted>
                  </td>
                  <td>
                    <Badge tone={DEPARTURE_STATUS_TONES[d.status]}>{DEPARTURE_STATUS_LABELS[d.status]}</Badge>
                  </td>
                  <td className="text-right tabular-nums">{d.capacity}</td>
                  <td className="text-right tabular-nums">
                    {d.reservedSeats > 0 ? (
                      <Link href={`/admin/bookings?productCode=${encodeURIComponent(code)}&departureDate=${d.date}`}>
                        {d.reservedSeats}
                      </Link>
                    ) : (
                      d.reservedSeats
                    )}
                  </td>
                  <td className="text-right tabular-nums">{d.remainingSeats}</td>
                  <td className="text-[13px] text-fg-muted">
                    {d.fromRule ? '규칙' : '추가'}
                    {d.stored ? (d.pinned ? ' · 고정' : ' · 저장됨') : ' · 계산됨'}
                  </td>
                  <td>
                    {!canceled && (
                      <Link href={`/admin/products/${encodeURIComponent(code)}/departures/${d.date}`}>
                        {d.costOverrides > 0 ? `조정 ${d.costOverrides}건` : '원가 조정'}
                      </Link>
                    )}
                  </td>
                  <td>
                    <div className="flex flex-wrap items-center gap-1.5">
                      {!canceled && (
                        <form
                          action={setCapacityAction.bind(null, code, d.date)}
                          className="inline-flex items-center gap-1"
                        >
                          <input type="hidden" name="from" value={from} />
                          <Input
                            name="capacity"
                            type="number"
                            min={Math.max(1, d.reservedSeats)}
                            max={999}
                            defaultValue={d.capacity}
                            aria-label={`${d.date} 정원`}
                            className="h-[30px] w-[74px] min-w-0 px-1.5"
                          />
                          <Button type="submit" variant="outline" size="sm">
                            정원 변경
                          </Button>
                        </form>
                      )}
                      {(closed || canceled) && (
                        <form action={reopenDepartureAction.bind(null, code, d.date)}>
                          <input type="hidden" name="from" value={from} />
                          <Button type="submit" variant="outline" size="sm">
                            다시 열기
                          </Button>
                        </form>
                      )}
                      {!closed && !canceled && (
                        <form action={closeDepartureAction.bind(null, code, d.date)}>
                          <input type="hidden" name="from" value={from} />
                          <ConfirmSubmit
                            variant="outline"
                            size="sm"
                            message={`${d.date} 출발을 마감할까요? 새 예약만 막힙니다.`}
                          >
                            마감
                          </ConfirmSubmit>
                        </form>
                      )}
                      {!canceled && (
                        <form action={cancelDepartureAction.bind(null, code, d.date)}>
                          <input type="hidden" name="from" value={from} />
                          <ConfirmSubmit
                            size="sm"
                            message={`${d.date} 출발을 취소할까요? 예약이 있으면 취소되지 않습니다.`}
                          >
                            출발 취소
                          </ConfirmSubmit>
                        </form>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
            {items.length === 0 && (
              <tr>
                <td colSpan={8} className="text-[13px] text-fg-muted">
                  이 기간에 출발일이 없습니다. 출발 규칙을 확인하거나 위에서 날짜를 추가하세요.
                </td>
              </tr>
            )}
          </tbody>
        </Table>
      </Card>

      <Pager
        summary="3개월 단위로 이동합니다."
        prev={
          prevFrom ? (
            <Link href={link(prevFrom)} className={pagerLinkClass}>
              ← 이전 3개월
            </Link>
          ) : (
            <PagerOff>← 이전 3개월</PagerOff>
          )
        }
        next={
          <Link href={link(to)} className={pagerLinkClass}>
            다음 3개월 →
          </Link>
        }
      />
    </>
  );
}
