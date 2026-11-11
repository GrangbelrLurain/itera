# 패키지 예약 설계

범위: 패키지 상세에서 **예약 생성 → 결제 → 취소/만료**까지. (결제는 모의 처리)

## 1. 흐름

```
상세 화면 ──예약하기──▶ 예약 폼(여행자·연락처) ──POST──▶ PENDING_PAYMENT
                                                        │  (좌석 확보, 결제 기한 24시간)
                              ┌─────────────────────────┼───────────────────┐
                           결제(모의)                 취소                 기한 경과
                              ▼                         ▼                   ▼
                          CONFIRMED ──취소(위약금)──▶ CANCELLED          EXPIRED
```

상태 전이는 서버만 수행한다. 종료 상태(`CANCELLED`, `EXPIRED`)에서는 어떤 전이도 불가.

## 2. 예약 생성 규칙

| 규칙 | 내용 |
|---|---|
| 판매 가능 | 상품이 활성이고, 날짜가 출발일 집합에 속하며(규칙 또는 저장된 출발), 출발일이 오늘 이후이고, 저장된 행이 있으면 상태가 `OPEN` |
| 인원 | 1~9명. 성인 1명 이상, 유아 수 ≤ 성인 수 |
| 대상 구분 | `ADULT`, `NOBED_CHILD`, `EXTRABED_CHILD`, `INFANT` 중 해당 상품에서 판매 가능(`isSalesAvailable`)한 것만 |
| 좌석 | 성인·소아는 좌석 1개씩 차지, 유아는 차지하지 않음 |
| 가격 | 여행자별 `discountedTotalPrice`(판매가+유류할증료+제세공과금−할인) 합계. 예약 시점 단가를 여행자 행에 스냅샷으로 저장 |
| 입력 검증 | 이름 필수(100자 이하), 생년월일 유효(미래 불가), 성별 `M`/`F`, 연락처 이름·전화 필수, 이메일 선택(형식 검사) |

## 3. 좌석 정합성 (핵심)

좌석 확보는 **조건부 UPDATE 한 문장**으로 원자적으로 수행한다.

```sql
UPDATE departure
   SET reserved_seats = reserved_seats + @n
 WHERE product_code = @p AND departure_date = @d
   AND status = 'OPEN' AND reserved_seats + @n <= capacity;   -- 영향 행 0이면 좌석 부족 또는 마감
```

출발일은 규칙에서 계산되는 가상 값이고, 이 행은 첫 예약 트랜잭션에서 만들어진다
(`docs/design/package-departure.md`).

예약 INSERT와 같은 트랜잭션이라 동시 요청이 몰려도 초과 예약이 생기지 않는다.
취소·만료 시에는 같은 트랜잭션에서 상태를 바꾸고(`WHERE status = 기대 상태`, 낙관적 조건) 좌석을 반환한다.
→ 같은 예약을 동시에 취소해도 좌석이 두 번 반환되지 않는다.

## 4. 결제 기한과 만료

- 생성 시 `paymentDueAt = 생성 시각 + 24시간`.
- 만료는 **지연 처리**한다: 예약 조회·결제·취소 시점에 기한이 지났으면 `EXPIRED`로 바꾸고 좌석을 반환한다. (스케줄러 없이도 일관된 상태를 보장. 운영에서는 주기 작업을 추가할 수 있음)

## 5. 취소와 위약금 (샘플 정책)

| 출발까지 남은 일수 | 위약금(결제 금액 대비) |
|---|---|
| 30일 이상 | 0% |
| 20~29일 | 10% |
| 10~19일 | 20% |
| 3~9일 | 30% |
| 0~2일 | 50% |

- `PENDING_PAYMENT` 취소: 결제된 금액이 없으므로 위약금·환불 모두 0.
- `CONFIRMED` 취소: `penalty = total × 비율`(내림), `refund = total − penalty`.

## 6. 데이터 모델

```
package_booking (booking_code PK) ── (product_code, departure_date) ▶ departure
 └─ package_booking_traveler (booking_code, seq) : 대상구분·이름·생년월일·성별·예약 시점 가격 스냅샷
```

상품명·출발일은 예약 시점 값을 예약 행에 스냅샷으로 둔다(상품이 이후 바뀌어도 예약 내역은 유지).

## 7. API (OpenAPI가 단일 원천)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/package-bookings` | 예약 생성(`productCode` + `departureDate`) → 201, `PENDING_PAYMENT` |
| GET | `/package-bookings/{code}` | 예약 조회 (만료 지연 처리 포함) |
| POST | `/package-bookings/{code}/pay` | 모의 결제 → `CONFIRMED` |
| POST | `/package-bookings/{code}/cancel` | 취소 → `CANCELLED` |

오류: 400 입력 오류, 404 없음, 409 좌석 부족, 422 허용되지 않는 상태 전이/판매 불가.

## 8. 의도적으로 뺀 것

실결제 연동, 결제 취소 API 호출, 알림(메일/SMS), 여행자 중복 검사, 연령 구분과 생년월일의 정합성 검사, 회원/권한. 포트폴리오 범위에서는 상태 머신과 좌석 정합성에 집중한다.
