import { BOOKING_STATUS_LABELS, BOOKING_STATUSES } from '@itera/domain/booking';
import { won } from '@itera/domain/format';
import { Card, Muted, PageTitle, Stat, StatGrid, Table } from '@itera/ui';
import Link from 'next/link';
import { LoadError } from '@/features/shell/page-parts';
import { type AdminDashboard, ApiError, adminApi } from '@/services/adminApi';
import { departureHref } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

const count = (n: number) => n.toLocaleString('ko-KR');

export default async function DashboardPage() {
  let data: AdminDashboard;
  try {
    data = await adminApi.dashboard();
  } catch (e) {
    if (e instanceof ApiError) return <LoadError message={e.message} />;
    throw e; // keeps Next.js redirects (expired session) working
  }

  const total = Object.values(data.statusCounts).reduce((a, b) => a + b, 0);

  return (
    <>
      <PageTitle>대시보드</PageTitle>
      <Muted as="p">예약 현황 요약입니다.</Muted>

      <StatGrid>
        <Stat as={Link} href="/admin/bookings" label="전체 예약" value={count(total)} />
        {BOOKING_STATUSES.map((s) => (
          <Stat
            key={s}
            as={Link}
            href={`/admin/bookings?status=${s}`}
            label={BOOKING_STATUS_LABELS[s]}
            value={count(data.statusCounts[s] ?? 0)}
          />
        ))}
        <Stat
          as={Link}
          href="/admin/bookings?status=PENDING_PAYMENT"
          label="24시간 내 결제 기한 만료 예정"
          value={count(data.pendingExpiringWithin24h)}
        />
        <Stat emphasis label="확정 예약 금액 합계" value={won(data.confirmedRevenue)} />
      </StatGrid>

      <Card title="잔여 좌석이 적은 모객중 출발 (5석 이하)">
        <Table>
          <thead>
            <tr>
              <th>상품</th>
              <th>출발일</th>
              <th className="text-right">잔여</th>
            </tr>
          </thead>
          <tbody>
            {data.lowSeatDepartures.map((p) => (
              <tr key={`${p.productCode}-${p.departureDate}`}>
                <td>
                  <Link href={departureHref(p.productCode, p.departureDate)}>{p.name}</Link>
                  <Muted as="div">{p.productCode}</Muted>
                </td>
                <td>{p.departureDate}</td>
                <td className="text-right tabular-nums">{p.remainingSeats}</td>
              </tr>
            ))}
            {data.lowSeatDepartures.length === 0 && (
              <tr>
                <td colSpan={3} className="text-[13px] text-fg-muted">
                  해당하는 출발이 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </Table>
      </Card>
    </>
  );
}
