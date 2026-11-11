import { BOOKING_STATUS_LABELS, BOOKING_STATUS_TONES } from '@itera/domain/booking';
import { formatDateTime, won } from '@itera/domain/format';
import { TARGET_LABELS } from '@itera/domain/pricing';
import { Badge, backLinkClass, Card, ConfirmSubmit, DescriptionList, Input, linkClass, Muted, Table } from '@itera/ui';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { cancelBookingAction, confirmPaymentAction } from '@/features/bookings/actions';
import { first, LoadError, Notices } from '@/features/shell/page-parts';
import { type AdminBookingDetail, ApiError, adminApi, type BookingDetail } from '@/services/adminApi';
import { departureHref } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

/** One term / description pair of a DescriptionList. */
function Item({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div>
      <dt>{term}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export default async function BookingDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ code: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { code } = await params;
  const sp = await searchParams;

  let adminDetail: AdminBookingDetail;
  try {
    adminDetail = await adminApi.getBooking(code);
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.status === 404) notFound();
      return <LoadError message={e.message} />;
    }
    throw e;
  }

  const booking: BookingDetail = adminDetail.booking;
  const canPay = booking.status === 'PENDING_PAYMENT';
  const canCancel = booking.status === 'PENDING_PAYMENT' || booking.status === 'CONFIRMED';

  return (
    <>
      <Link href="/admin/bookings" className={backLinkClass}>
        ← 예약 목록
      </Link>
      <Notices msg={first(sp.msg)} error={first(sp.error)} />

      <Card>
        <Badge tone={BOOKING_STATUS_TONES[booking.status]}>{BOOKING_STATUS_LABELS[booking.status]}</Badge>
        <h1 className="mt-2 mb-1 font-bold text-2xl">{booking.packageName}</h1>
        <Muted as="p">
          예약번호 <strong>{booking.bookingCode}</strong> · {booking.productCode} · 출발 {booking.departureDate} · 채널{' '}
          {booking.channelCode} ·{' '}
          <Link href={departureHref(booking.productCode, booking.departureDate)} className={linkClass}>
            상품 보기
          </Link>
        </Muted>
        {canPay && (
          <p className="mt-3">
            결제 기한: <strong>{formatDateTime(booking.paymentDueAt)}</strong> (KST)
          </p>
        )}
      </Card>

      {(canPay || canCancel) && (
        <Card title="처리">
          <div className="grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-4">
            {canPay && (
              <form action={confirmPaymentAction.bind(null, booking.bookingCode)}>
                <strong>결제 확인</strong>
                <Muted as="p">입금/결제를 확인하면 예약이 확정됩니다.</Muted>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <Input
                    name="paymentReference"
                    placeholder="결제 번호 (필수)"
                    required
                    maxLength={100}
                    className="min-w-[220px] flex-1"
                  />
                  <ConfirmSubmit variant="primary" message="결제를 확인 처리할까요?">
                    결제 확인
                  </ConfirmSubmit>
                </div>
              </form>
            )}
            {canCancel && (
              <form action={cancelBookingAction.bind(null, booking.bookingCode)}>
                <strong>예약 취소</strong>
                <Muted as="p">
                  {booking.status === 'CONFIRMED'
                    ? '확정된 예약은 출발까지 남은 기간에 따라 위약금이 부과됩니다.'
                    : '결제 전 예약이라 위약금 없이 취소되고 좌석이 반환됩니다.'}
                </Muted>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <Input
                    name="reason"
                    placeholder="취소 사유 (선택)"
                    maxLength={500}
                    className="min-w-[220px] flex-1"
                  />
                  <ConfirmSubmit message="예약을 취소할까요? 되돌릴 수 없습니다.">예약 취소</ConfirmSubmit>
                </div>
              </form>
            )}
          </div>
        </Card>
      )}

      <Card title="결제 내역">
        <DescriptionList>
          <Item term="총 금액">{won(booking.totalAmount)}</Item>
          <Item term="좌석">{booking.seatCount}석</Item>
          <Item term="결제 번호">{booking.paymentReference ?? '-'}</Item>
          <Item term="예약 일시">{formatDateTime(booking.createdAt)}</Item>
          {booking.confirmedAt && <Item term="결제 일시">{formatDateTime(booking.confirmedAt)}</Item>}
          {booking.cancelledAt && (
            <>
              <Item term="취소 일시">{formatDateTime(booking.cancelledAt)}</Item>
              <Item term="위약금">{won(booking.penaltyAmount)}</Item>
              <Item term="환불액">{won(booking.refundAmount)}</Item>
              <Item term="취소 사유">{booking.cancelReason ?? '-'}</Item>
            </>
          )}
        </DescriptionList>
      </Card>

      <Card title="여행자">
        <Table>
          <thead>
            <tr>
              <th>#</th>
              <th>구분</th>
              <th>이름</th>
              <th>생년월일</th>
              <th>성별</th>
              <th className="text-right">요금</th>
            </tr>
          </thead>
          <tbody>
            {booking.travelers.map((t) => (
              <tr key={t.seq}>
                <td>{t.seq}</td>
                <td>{TARGET_LABELS[t.targetType]}</td>
                <td>{t.name}</td>
                <td>{t.birthDate}</td>
                <td>{t.gender === 'M' ? '남' : '여'}</td>
                <td className="text-right tabular-nums">{won(t.unitPrice)}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Card>

      <Card title="원가 · 수수료 (내부용)">
        <Table>
          <thead>
            <tr>
              <th>#</th>
              <th className="text-right">판매가</th>
              <th className="text-right">할인</th>
              <th className="text-right">통과(유류·세금)</th>
              <th className="text-right">원가</th>
              <th className="text-right">수수료</th>
            </tr>
          </thead>
          <tbody>
            {adminDetail.finance.map((f) => (
              <tr key={f.seq}>
                <td>{f.seq}</td>
                <td className="text-right tabular-nums">{won(f.unitSalePrice)}</td>
                <td className="text-right tabular-nums">{f.unitDiscount > 0 ? `-${won(f.unitDiscount)}` : '-'}</td>
                <td className="text-right tabular-nums">{won(f.unitPassThrough)}</td>
                <td className="text-right tabular-nums">
                  {f.unitCost === null || f.unitCost === undefined ? '-' : won(f.unitCost)}
                </td>
                <td className="text-right tabular-nums">
                  {f.unitCommission === null || f.unitCommission === undefined ? '-' : won(f.unitCommission)}
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
        <DescriptionList>
          <Item term="채널">{booking.channelCode}</Item>
          <Item term="총 원가">
            {adminDetail.totalCost === null || adminDetail.totalCost === undefined
              ? '알 수 없음 (수동 가격 포함)'
              : won(adminDetail.totalCost)}
          </Item>
          <Item term="총 수수료">{won(adminDetail.totalCommission)}</Item>
          <Item term="순수익">
            {adminDetail.profit === null || adminDetail.profit === undefined ? (
              '-'
            ) : (
              <strong className={adminDetail.profit < 0 ? 'text-danger' : undefined}>{won(adminDetail.profit)}</strong>
            )}
          </Item>
        </DescriptionList>
        <Muted as="p">예약 시점에 저장된 값입니다. 이후 가격·수수료 정책이 바뀌어도 이 예약은 변하지 않습니다.</Muted>
      </Card>

      <Card title="연락처">
        <DescriptionList>
          <Item term="이름">{booking.contact.name}</Item>
          <Item term="전화번호">{booking.contact.phone}</Item>
          <Item term="이메일">{booking.contact.email ?? '-'}</Item>
        </DescriptionList>
      </Card>
    </>
  );
}
