import {
  DEPARTURE_STATUS_FILTERS,
  DEPARTURE_STATUS_TONES,
  PUBLIC_DEPARTURE_STATUS_LABELS,
  WEEKDAYS,
} from '@itera/domain/departure';
import { won } from '@itera/domain/format';
import {
  Badge,
  Button,
  Card,
  Check,
  Chip,
  Field,
  FieldGroup,
  Input,
  linkClass,
  Muted,
  Pager,
  PagerOff,
  PageTitle,
  pagerLinkClass,
  Table,
} from '@itera/ui';
import Link from 'next/link';
import { LoadError, type SearchParams } from '@/features/shell/page-parts';
import { ApiError, departureHref, listPackages, type PackageList, type PackageListQuery } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

const PAGE_SIZE = 20;

function many(v: string | string[] | undefined): string[] {
  return v === undefined ? [] : Array.isArray(v) ? v : [v];
}
function one(v: string | string[] | undefined): string {
  return many(v)[0] ?? '';
}

export default async function PackageListPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const sp = await searchParams;
  const from = one(sp.from);
  const to = one(sp.to);
  const keyword = one(sp.keyword);
  const statuses = many(sp.status)
    .flatMap((s) => s.split(','))
    .filter(Boolean);
  const weekdays = many(sp.weekdays)
    .flatMap((s) => s.split(','))
    .filter(Boolean);
  const page = Math.max(0, Number.parseInt(one(sp.page), 10) || 0);

  const query: PackageListQuery = {
    from: from || undefined,
    to: to || undefined,
    keyword: keyword || undefined,
    status: statuses.length ? statuses.join(',') : undefined,
    weekdays: weekdays.length ? weekdays.join(',') : undefined,
    page,
    size: PAGE_SIZE,
  };
  const filtered = Boolean(from || to || keyword || statuses.length || weekdays.length);

  let list: PackageList | null = null;
  let error: string | null = null;
  try {
    list = await listPackages(query);
  } catch (e) {
    error = e instanceof ApiError ? e.message : '백엔드에 연결할 수 없습니다.';
  }

  const pageCount = list ? Math.max(1, Math.ceil(list.total / PAGE_SIZE)) : 1;
  const pageHref = (p: number) => {
    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (keyword) params.set('keyword', keyword);
    if (statuses.length) params.set('status', statuses.join(','));
    if (weekdays.length) params.set('weekdays', weekdays.join(','));
    if (p > 0) params.set('page', String(p));
    const qs = params.toString();
    return `/admin/packages${qs ? `?${qs}` : ''}`;
  };

  return (
    <>
      <PageTitle>출발 조회</PageTitle>
      <Muted as="p">
        출발일은 상품의 출발 규칙에서 계산됩니다. 예약이 생기거나 관리자가 조치한 날짜만 DB에 저장됩니다.
      </Muted>

      <form method="get" className="mt-4">
        <Card tone="filter">
          <div className="flex flex-wrap items-end gap-x-7 gap-y-3">
            <Field label="출발일">
              <span className="flex items-center gap-1.5">
                <Input type="date" name="from" defaultValue={from} />
                <em className="text-fg-muted not-italic">~</em>
                <Input type="date" name="to" defaultValue={to} />
              </span>
            </Field>
            <Field label="키워드" className="flex-[1_1_220px]">
              <Input type="search" name="keyword" defaultValue={keyword} placeholder="상품명 또는 상품코드" />
            </Field>
          </div>

          <div className="mt-3 flex flex-wrap items-end gap-x-7 gap-y-3">
            <FieldGroup legend="상태">
              <span className="flex h-[34px] items-center gap-3.5">
                {DEPARTURE_STATUS_FILTERS.map((o) => (
                  <Check key={o.value} name="status" value={o.value} defaultChecked={statuses.includes(o.value)}>
                    {o.label}
                  </Check>
                ))}
              </span>
            </FieldGroup>
            <FieldGroup legend="출발 요일">
              <span className="flex gap-1">
                {WEEKDAYS.map((o) => (
                  <Chip key={o.value} name="weekdays" value={o.value} defaultChecked={weekdays.includes(o.value)}>
                    {o.label}
                  </Chip>
                ))}
              </span>
            </FieldGroup>
            <span className="ml-auto flex items-center gap-3">
              <Button type="submit">조회</Button>
              {filtered && (
                <Link href="/admin/packages" className={linkClass}>
                  초기화
                </Link>
              )}
            </span>
          </div>
        </Card>
      </form>

      {error || !list ? (
        <LoadError message={error ?? ''} />
      ) : (
        <>
          <p className="mt-1 mb-2 text-sm">
            총 <strong>{list.total.toLocaleString('ko-KR')}</strong>건
          </p>
          <Card className="p-0">
            <Table>
              <thead>
                <tr>
                  <th>상품</th>
                  <th>출발일</th>
                  <th>일정</th>
                  <th>상태</th>
                  <th className="text-right">잔여</th>
                  <th className="text-right">성인 1인</th>
                </tr>
              </thead>
              <tbody>
                {list.items.map((p) => (
                  <tr key={`${p.productCode}-${p.departureDate}`}>
                    <td>
                      <Link href={departureHref(p.productCode, p.departureDate)}>{p.name}</Link>
                      <Muted as="div">{p.productCode}</Muted>
                    </td>
                    <td>{p.departureDate}</td>
                    <td>
                      {p.nights}박 {p.days}일
                    </td>
                    <td>
                      <Badge tone={DEPARTURE_STATUS_TONES[p.status]}>{PUBLIC_DEPARTURE_STATUS_LABELS[p.status]}</Badge>
                    </td>
                    <td className="text-right tabular-nums">{p.remainingSeats}</td>
                    <td className="text-right tabular-nums">
                      {p.adultDiscountedTotalPrice < p.adultTotalPrice && (
                        <span className="mr-1.5 text-fg-dim line-through">{won(p.adultTotalPrice)}</span>
                      )}
                      {won(p.adultDiscountedTotalPrice)}
                    </td>
                  </tr>
                ))}
                {list.items.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-[13px] text-fg-muted">
                      조건에 맞는 출발이 없습니다.
                    </td>
                  </tr>
                )}
              </tbody>
            </Table>
          </Card>

          <Pager
            summary={
              <>
                {page + 1} / {pageCount} 페이지
              </>
            }
            prev={
              page > 0 ? (
                <Link href={pageHref(page - 1)} className={pagerLinkClass}>
                  ← 이전
                </Link>
              ) : (
                <PagerOff>← 이전</PagerOff>
              )
            }
            next={
              page + 1 < pageCount ? (
                <Link href={pageHref(page + 1)} className={pagerLinkClass}>
                  다음 →
                </Link>
              ) : (
                <PagerOff>다음 →</PagerOff>
              )
            }
          />
        </>
      )}
    </>
  );
}
