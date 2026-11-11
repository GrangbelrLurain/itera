import { DEPARTURE_STATUS_TONES, PUBLIC_DEPARTURE_STATUS_LABELS } from '@itera/domain/departure';
import { won } from '@itera/domain/format';
import { TARGET_LABELS, TARGET_TYPES } from '@itera/domain/pricing';
import { SERVICE_TYPE_LABELS } from '@itera/domain/product';
import { Badge, backLinkClass, Card, DescriptionList, linkClass, Muted, Table } from '@itera/ui';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { LoadError } from '@/features/shell/page-parts';
import { ApiError, getPackage, type PackageDetail } from '@/services/packageApi';

export const dynamic = 'force-dynamic';

/** One term / description pair of a DescriptionList. */
function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children ?? '-'}</dd>
    </div>
  );
}

function yn(v: boolean | null | undefined) {
  return v === true ? '예' : v === false ? '아니오' : '-';
}

/** A block of free text (line breaks kept). */
function Text({ value }: { value?: string | null }) {
  return value ? <p className="whitespace-pre-wrap text-sm leading-relaxed">{value}</p> : <Muted as="p">-</Muted>;
}

function Heading({ children }: { children: React.ReactNode }) {
  return <h3 className="text-[13px] text-fg-muted">{children}</h3>;
}

export default async function PackageDetailPage({
  params,
}: {
  params: Promise<{ productCode: string; date: string }>;
}) {
  const { productCode: rawCode, date } = await params;
  const productCode = decodeURIComponent(rawCode);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) notFound();

  let detail: PackageDetail;
  try {
    detail = await getPackage(productCode, date);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    return <LoadError message={e instanceof ApiError ? e.message : '백엔드에 연결할 수 없습니다.'} />;
  }

  const { basic: b, remark, condition: c } = detail;
  const prices = [...detail.prices].sort(
    (a, z) => TARGET_TYPES.indexOf(a.targetType) - TARGET_TYPES.indexOf(z.targetType),
  );

  return (
    <>
      <Link href="/admin/packages" className={backLinkClass}>
        ← 패키지 목록
      </Link>

      <Card>
        <Badge tone={DEPARTURE_STATUS_TONES[b.status]}>{PUBLIC_DEPARTURE_STATUS_LABELS[b.status]}</Badge>{' '}
        {b.promotionTitle && <Badge tone="warning">{b.promotionTitle}</Badge>}
        <h1 className="mt-2 mb-1 font-bold text-2xl">{b.name}</h1>
        <Muted as="p">
          {b.productCode} · 출발 {b.departureDate}
        </Muted>
        {b.highlight && <p className="mt-3">{b.highlight}</p>}
        <p className="mt-3">
          <Link
            className={linkClass}
            href={`/admin/bookings?productCode=${encodeURIComponent(b.productCode)}&departureDate=${b.departureDate}`}
          >
            이 출발의 예약 보기 →
          </Link>
        </p>
      </Card>

      <Card title="기본 정보">
        <DescriptionList>
          <Item label="출발일">{b.departureDate}</Item>
          <Item label="일정">
            {b.nights}박 {b.days}일
          </Item>
          <Item label="출발">{b.departureInfo}</Item>
          <Item label="도착">{b.arrivalInfo}</Item>
          <Item label="직항">{yn(b.isDirectFlight)}</Item>
          <Item label="비자 필수">{yn(b.isVisaRequired)}</Item>
          <Item label="미팅">
            {b.meetingTime} {b.meetingLocation && `(${b.meetingLocation})`}
          </Item>
          <Item label="대표 호텔">
            {b.hotelName} {b.hotelStars != null && `· ${b.hotelStars}성급`}
          </Item>
          <Item label="무료 취소 기한">{b.freeCancellationUntil}</Item>
        </DescriptionList>
      </Card>

      <Card title="좌석">
        <DescriptionList>
          <Item label="정원">{b.capacity}</Item>
          <Item label="예약 좌석">{b.reservedSeats}</Item>
          <Item label="잔여 좌석">{b.remainingSeats}</Item>
          <Item label="저장 여부">
            {b.isStoredDeparture ? '저장됨 (예약 또는 관리자 조치 있음)' : '계산된 출발일 (아직 저장 안 됨)'}
          </Item>
        </DescriptionList>
      </Card>

      <Card title="요금 (1인)">
        <Table>
          <thead>
            <tr>
              <th>구분</th>
              <th className="text-right">판매가</th>
              <th className="text-right">할인</th>
              <th className="text-right">유류할증료</th>
              <th className="text-right">제세공과금</th>
              <th className="text-right">결제액</th>
            </tr>
          </thead>
          <tbody>
            {prices.map((p) => (
              <tr key={p.targetType}>
                <td>
                  {TARGET_LABELS[p.targetType]}
                  {!p.isSalesAvailable && <Muted> (판매 안 함)</Muted>}
                </td>
                <td className="text-right tabular-nums">{won(p.salePrice)}</td>
                <td className="text-right tabular-nums">{p.discount > 0 ? `-${won(p.discount)}` : '-'}</td>
                <td className="text-right tabular-nums">{won(p.fuelSurcharge)}</td>
                <td className="text-right tabular-nums">{won(p.taxesAndCharges)}</td>
                <td className="text-right tabular-nums">
                  {p.discountedTotalPrice < p.totalPrice && (
                    <span className="mr-1.5 text-fg-dim line-through">{won(p.totalPrice)}</span>
                  )}
                  <strong>{won(p.discountedTotalPrice)}</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
        <Text value={remark?.priceNote} />
      </Card>

      <Card title="일정">
        {detail.itinerary.days.map((d) => (
          <div key={d.day} className="mb-3.5">
            <h3 className="mb-1.5 text-primary-strong text-sm">
              {d.day}일차 · {d.date}
            </h3>
            <ul className="list-none p-0">
              {d.items.map((i, idx) => (
                // biome-ignore lint/suspicious/noArrayIndexKey: read-only list, items have no id
                <li key={`${d.day}-${idx}`} className="border-line border-b border-dashed py-1.5 text-sm">
                  <span className="mr-1.5 text-[11px] text-primary-strong">{SERVICE_TYPE_LABELS[i.serviceType]}</span>
                  <strong>{i.placeName}</strong>
                  {i.isHighlight && <span className="text-[11px] text-primary-strong"> ★</span>}
                  {i.isPerk && <span className="text-[11px] text-primary-strong"> 특전</span>}
                  {i.description && <Muted as="div">{i.description}</Muted>}
                </li>
              ))}
            </ul>
          </div>
        ))}
        {detail.itinerary.days.length === 0 && <Muted as="p">등록된 일정이 없습니다.</Muted>}
      </Card>

      {detail.optionalTours.length > 0 && (
        <Card title="선택관광">
          <Table>
            <thead>
              <tr>
                <th>이름</th>
                <th>장소</th>
                <th className="text-right">성인</th>
                <th className="text-right">아동</th>
                <th className="text-right">최소인원</th>
                <th className="text-right">소요(분)</th>
              </tr>
            </thead>
            <tbody>
              {detail.optionalTours.map((t) => (
                <tr key={t.seq}>
                  <td>
                    {t.name}
                    {t.summary && <Muted as="div">{t.summary}</Muted>}
                  </td>
                  <td>{t.placeName ?? '-'}</td>
                  <td className="text-right tabular-nums">
                    {t.priceAdult != null ? `${t.priceAdult} ${t.currency ?? ''}` : '-'}
                  </td>
                  <td className="text-right tabular-nums">
                    {t.priceChild != null ? `${t.priceChild} ${t.currency ?? ''}` : '-'}
                  </td>
                  <td className="text-right tabular-nums">{t.minParticipants ?? '-'}</td>
                  <td className="text-right tabular-nums">{t.durationMinutes ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}

      {detail.shoppings.length > 0 && (
        <Card title="쇼핑">
          <Table>
            <thead>
              <tr>
                <th>품목</th>
                <th>장소</th>
                <th className="text-right">소요(분)</th>
                <th>환불</th>
              </tr>
            </thead>
            <tbody>
              {detail.shoppings.map((s) => (
                <tr key={s.seq}>
                  <td>{s.itemName}</td>
                  <td>{s.placeName ?? '-'}</td>
                  <td className="text-right tabular-nums">{s.durationMinutes ?? '-'}</td>
                  <td>{yn(s.isRefundable)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}

      {remark && (
        <Card title="포함 · 불포함 · 유의사항">
          <Heading>포함</Heading>
          <Text value={remark.inclusions} />
          <Heading>불포함</Heading>
          <Text value={remark.exclusions} />
          <Heading>상품 포인트</Heading>
          <Text value={remark.highlights} />
          <Heading>예약 시 유의사항</Heading>
          <Text value={remark.bookingNotice} />
          <Heading>여행 시 유의사항</Heading>
          <Text value={remark.travelNotice} />
          <Heading>객실 정보</Heading>
          <Text value={remark.roomInfo} />
          <Heading>자주 묻는 질문</Heading>
          <Text value={remark.faq} />
        </Card>
      )}

      {c && (
        <Card title="조건">
          <DescriptionList>
            <Item label="최소 출발 인원">{c.minDeparturePopulation}</Item>
            <Item label="항공 좌석 등급">{c.airlineSeatClass}</Item>
            <Item label="인솔자">{yn(c.hasTourLeader)}</Item>
            <Item label="현지 가이드">{c.localGuide}</Item>
            <Item label="여행자 보험">{yn(c.isInsuranceIncluded)}</Item>
            <Item label="쇼핑 횟수">{c.shoppingCount}</Item>
            <Item label="선택관광 포함">{yn(c.isOptionalTourIncluded)}</Item>
            <Item label="관광지 입장료 포함">{yn(c.isAdmissionFeeIncluded)}</Item>
            <Item label="현지 필수 경비 (성인/아동/유아)">
              {c.localExpensesAdult} / {c.localExpensesChild} / {c.localExpensesInfant} {c.localExpensesCurrency}
            </Item>
            <Item label="싱글룸 사용료">{won(c.singleRoomFee)}</Item>
          </DescriptionList>
          <Heading>취소 약관</Heading>
          <Text value={c.cancellationPolicy} />
        </Card>
      )}
    </>
  );
}
