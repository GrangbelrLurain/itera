import { BOOKING_STATUS_LABELS, BOOKING_STATUS_TONES, BOOKING_STATUSES } from '@itera/domain/booking';
import { formatDateTime, won } from '@itera/domain/format';
import {
  Badge,
  Button,
  Card,
  Check,
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
import { type AdminBookingList, ApiError, adminApi } from '@/services/adminApi';

export const dynamic = 'force-dynamic';

const PAGE_SIZE = 20;

function many(v: string | string[] | undefined): string[] {
  return v === undefined ? [] : Array.isArray(v) ? v : [v];
}
function one(v: string | string[] | undefined): string {
  return many(v)[0] ?? '';
}

export default async function BookingsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const sp = await searchParams;
  // ?status=A,B (links) and ?status=A&status=B (the form) are both accepted
  const statuses = many(sp.status)
    .flatMap((s) => s.split(','))
    .filter(Boolean);
  const keyword = one(sp.keyword);
  const from = one(sp.from);
  const to = one(sp.to);
  const productCode = one(sp.productCode);
  const departureDate = one(sp.departureDate);
  const page = Math.max(0, Number.parseInt(one(sp.page), 10) || 0);

  let list: AdminBookingList;
  try {
    list = await adminApi.listBookings({
      status: statuses.length ? statuses.join(',') : undefined,
      keyword: keyword || undefined,
      from: from || undefined,
      to: to || undefined,
      productCode: productCode || undefined,
      departureDate: departureDate || undefined,
      page,
      size: PAGE_SIZE,
    });
  } catch (e) {
    if (e instanceof ApiError) return <LoadError message={e.message} />;
    throw e;
  }

  const filtered = statuses.length > 0 || keyword || from || to || productCode || departureDate;
  const pageCount = Math.max(1, Math.ceil(list.total / PAGE_SIZE));
  const pageHref = (p: number) => {
    const params = new URLSearchParams();
    if (statuses.length) params.set('status', statuses.join(','));
    if (keyword) params.set('keyword', keyword);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    if (productCode) params.set('productCode', productCode);
    if (departureDate) params.set('departureDate', departureDate);
    if (p > 0) params.set('page', String(p));
    const qs = params.toString();
    return `/admin/bookings${qs ? `?${qs}` : ''}`;
  };

  return (
    <>
      <PageTitle>예약 관리</PageTitle>
      <Muted as="p">최신 예약부터 표시합니다. 결제 기한이 지난 예약은 조회할 때 자동으로 만료 처리됩니다.</Muted>

      <form method="get" className="mt-4">
        <Card tone="filter">
          <div className="flex flex-wrap items-end gap-x-7 gap-y-3">
            <Field label="검색" className="flex-[1_1_220px]">
              <Input
                type="search"
                name="keyword"
                defaultValue={keyword}
                placeholder="예약번호, 예약자 이름·전화번호, 상품명"
              />
            </Field>
            <Field label="예약일">
              <span className="flex items-center gap-1.5">
                <Input type="date" name="from" defaultValue={from} />
                <em className="text-fg-muted not-italic">~</em>
                <Input type="date" name="to" defaultValue={to} />
              </span>
            </Field>
            {productCode && <input type="hidden" name="productCode" value={productCode} />}
            {departureDate && <input type="hidden" name="departureDate" value={departureDate} />}
          </div>
          <div className="mt-3 flex flex-wrap items-end gap-x-7 gap-y-3">
            <FieldGroup legend="상태">
              <span className="flex h-[34px] items-center gap-3.5">
                {BOOKING_STATUSES.map((s) => (
                  <Check key={s} name="status" value={s} defaultChecked={statuses.includes(s)}>
                    {BOOKING_STATUS_LABELS[s]}
                  </Check>
                ))}
              </span>
            </FieldGroup>
            <span className="ml-auto flex items-center gap-3">
              <Button type="submit">조회</Button>
              {filtered && (
                <Link href="/admin/bookings" className={linkClass}>
                  초기화
                </Link>
              )}
            </span>
          </div>
          {productCode && (
            <Muted as="p" className="mt-2">
              상품 {productCode}
              {departureDate && ` · 출발 ${departureDate}`}의 예약만 표시 중입니다.
            </Muted>
          )}
        </Card>
      </form>

      <p className="mt-1 mb-2 text-sm">
        총 <strong>{list.total.toLocaleString('ko-KR')}</strong>건
      </p>
      <Card className="p-0">
        <Table>
          <thead>
            <tr>
              <th>예약번호</th>
              <th>상품</th>
              <th>출발일</th>
              <th>예약자</th>
              <th>상태</th>
              <th className="text-right">좌석</th>
              <th className="text-right">금액</th>
              <th>예약일시</th>
            </tr>
          </thead>
          <tbody>
            {list.items.map((b) => (
              <tr key={b.bookingCode}>
                <td>
                  <Link href={`/admin/bookings/${b.bookingCode}`}>{b.bookingCode}</Link>
                </td>
                <td>
                  {b.packageName}
                  <Muted as="div">{b.productCode}</Muted>
                </td>
                <td>{b.departureDate}</td>
                <td>
                  {b.contactName}
                  <Muted as="div">{b.contactPhone}</Muted>
                </td>
                <td>
                  <Badge tone={BOOKING_STATUS_TONES[b.status]}>{BOOKING_STATUS_LABELS[b.status]}</Badge>
                </td>
                <td className="text-right tabular-nums">{b.seatCount}</td>
                <td className="text-right tabular-nums">{won(b.totalAmount)}</td>
                <td>{formatDateTime(b.createdAt)}</td>
              </tr>
            ))}
            {list.items.length === 0 && (
              <tr>
                <td colSpan={8} className="text-[13px] text-fg-muted">
                  조건에 맞는 예약이 없습니다.
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
  );
}
