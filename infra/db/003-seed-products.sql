USE AeroCore;
GO

-- SAMPLE DATA for local development only (not real products or prices).
-- weekdays bitmask: MON=1 TUE=2 WED=4 THU=8 FRI=16 SAT=32 SUN=64
DECLARE @p TABLE (
    code NVARCHAR(20), name NVARCHAR(100), grade NVARCHAR(30), nights INT, days INT,
    dep_route NVARCHAR(20), ret_route NVARCHAR(20), hotel NVARCHAR(200), stars TINYINT,
    capacity INT, weekdays INT, adult BIGINT, fuel BIGINT, tax BIGINT);

INSERT @p VALUES
 (N'DAD501', N'다낭 호이안 4박5일',     N'프리미엄', 4, 5, N'ICN-DAD', N'DAD-ICN', N'다낭 리조트 (샘플)',     5, 30, 36, 1290000, 90000, 45000),
 (N'TYO201', N'도쿄 자유여행 3박4일',   N'실속',     3, 4, N'ICN-NRT', N'NRT-ICN', N'도쿄 시내 호텔 (샘플)',   3, 40, 16,  890000, 70000, 38000),
 (N'BKK501', N'방콕 파타야 4박6일',     N'실속',     4, 6, N'ICN-BKK', N'BKK-ICN', N'파타야 호텔 (샘플)',     4, 30, 32,  990000, 70000, 38000),
 (N'CEB401', N'세부 리조트 3박5일',     N'프리미엄', 3, 5, N'ICN-CEB', N'CEB-ICN', N'세부 리조트 (샘플)',     5, 24, 64, 1150000, 70000, 38000),
 (N'FUK201', N'후쿠오카 온천 2박3일',   N'실속',     2, 3, N'ICN-FUK', N'FUK-ICN', N'후쿠오카 온천 호텔 (샘플)', 4, 36, 96,  590000, 60000, 38000),
 (N'HAN401', N'하노이 하롱베이 3박5일', N'실속',     3, 5, N'ICN-HAN', N'HAN-ICN', N'하노이 호텔 (샘플)',     4, 28,  2,  840000, 70000, 38000),
 (N'GUM401', N'괌 가족 리조트 4박5일',  N'실속',     4, 5, N'ICN-GUM', N'GUM-ICN', N'괌 리조트 (샘플)',       4, 20, 32, 1490000, 90000, 45000),
 (N'OSA301', N'오사카 교토 2박3일',     N'실속',     2, 3, N'ICN-KIX', N'KIX-ICN', N'오사카 시내 호텔 (샘플)', 4, 20, 17,  690000, 60000, 38000);

INSERT dbo.product (product_code, name, grade, nights, days, departure_route, departure_time, return_route, return_time,
    direct_flight, visa_required, meeting_time, meeting_location, hotel_name, hotel_stars, highlight,
    promotion_title, discount_amount, default_capacity, active)
SELECT p.code, p.name, p.grade, p.nights, p.days, p.dep_route, N'09:30', p.ret_route, N'20:10',
    1, 0, N'07:30', N'인천공항 제1터미널', p.hotel, p.stars,
    CASE p.code WHEN N'DAD501' THEN N'동급 상품 대비 쇼핑 1회, 전 일정 5성급 호텔 (샘플 문구)' END,
    CASE p.code WHEN N'DAD501' THEN N'얼리버드 특가' END,
    CASE p.code WHEN N'DAD501' THEN 50000 ELSE 0 END,
    p.capacity, 1
FROM @p p WHERE NOT EXISTS (SELECT 1 FROM dbo.product x WHERE x.product_code = p.code);

INSERT dbo.product_price (product_code, target_type, base_price, fuel_surcharge, taxes_and_charges, sales_available)
SELECT p.code, t.target_type,
    CASE t.target_type WHEN N'ADULT' THEN p.adult WHEN N'INFANT' THEN 100000 ELSE p.adult - 100000 END,
    CASE t.target_type WHEN N'INFANT' THEN 0 ELSE p.fuel END,
    CASE t.target_type WHEN N'INFANT' THEN 12000 ELSE p.tax END,
    CASE WHEN t.target_type = N'EXTRABED_CHILD' AND p.code <> N'DAD501' THEN 0 ELSE 1 END
FROM @p p
CROSS JOIN (VALUES (N'ADULT'), (N'NOBED_CHILD'), (N'EXTRABED_CHILD'), (N'INFANT')) AS t(target_type)
WHERE NOT EXISTS (SELECT 1 FROM dbo.product_price x WHERE x.product_code = p.code AND x.target_type = t.target_type);

-- One rule per product: every matching weekday from 2027-03-01 to 2027-05-31
INSERT dbo.departure_rule (product_code, valid_from, valid_to, weekdays, active)
SELECT p.code, '2027-03-01', '2027-05-31', p.weekdays, 1
FROM @p p WHERE NOT EXISTS (SELECT 1 FROM dbo.departure_rule r WHERE r.product_code = p.code);

IF NOT EXISTS (SELECT 1 FROM dbo.rule_exclusion WHERE product_code = N'DAD501' AND excluded_date = '2027-05-05')
    INSERT dbo.rule_exclusion (product_code, excluded_date) VALUES (N'DAD501', '2027-05-05');

-- Minimal itinerary (first/last day) for every product that has none yet
INSERT dbo.product_itinerary (product_code, day_no, sort_order, service_type, place_name, description)
SELECT p.code, d.day_no, 1, N'FLIGHT', d.place, d.descr
FROM @p p
CROSS APPLY (VALUES (1, N'인천 출발', N'샘플 일정: 출발 및 현지 도착'), (p.days, N'귀국', N'샘플 일정: 귀국')) AS d(day_no, place, descr)
WHERE p.code <> N'DAD501'
  AND NOT EXISTS (SELECT 1 FROM dbo.product_itinerary i WHERE i.product_code = p.code);
GO

-- Stored departures: sample states (these dates already have bookings made elsewhere, or were set by an admin)
INSERT dbo.departure (product_code, departure_date, capacity, reserved_seats, status, pinned)
SELECT v.code, v.d, v.cap, v.reserved, v.status, v.pinned
FROM (VALUES
    (N'DAD501', CAST('2027-03-10' AS DATE), 30, 12, N'OPEN',   0),   -- Wed: some seats taken
    (N'DAD501', CAST('2027-03-13' AS DATE), 30, 30, N'CLOSED', 1),   -- Sat: closed by admin
    (N'CEB401', CAST('2027-03-21' AS DATE), 24, 24, N'OPEN',   0),   -- Sun: sold out (derived FULL)
    (N'HAN401', CAST('2027-03-09' AS DATE), 28, 26, N'OPEN',   0),   -- Tue: few seats left
    (N'GUM401', CAST('2027-03-13' AS DATE), 20, 18, N'OPEN',   0),   -- Sat: few seats left
    (N'OSA301', CAST('2027-03-12' AS DATE), 20,  0, N'CLOSED', 1),   -- Fri: closed by admin
    (N'BKK501', CAST('2027-03-13' AS DATE), 30, 10, N'OPEN',   0)    -- Sat
) AS v(code, d, cap, reserved, status, pinned)
WHERE NOT EXISTS (SELECT 1 FROM dbo.departure x WHERE x.product_code = v.code AND x.departure_date = v.d);
GO

-- Full content for the main sample product
IF NOT EXISTS (SELECT 1 FROM dbo.product_itinerary WHERE product_code = N'DAD501')
BEGIN
    INSERT dbo.product_itinerary (product_code, day_no, sort_order, service_type, place_name, description, is_highlight, is_perk) VALUES
        (N'DAD501', 1, 1, N'FLIGHT',      N'인천 → 다낭',   N'인천공항 출발, 다낭 도착 후 호텔 이동', 0, 0),
        (N'DAD501', 1, 2, N'HOTEL',       N'다낭 리조트',   N'호텔 체크인 및 휴식', 0, 0),
        (N'DAD501', 2, 1, N'SIGHTSEEING', N'바나힐',        N'바나힐 케이블카와 골든브릿지 관광', 1, 0),
        (N'DAD501', 2, 2, N'MEAL',        N'현지식',        N'현지식 중식', 0, 0),
        (N'DAD501', 3, 1, N'SIGHTSEEING', N'호이안 구시가지', N'호이안 올드타운 도보 관광', 1, 1),
        (N'DAD501', 4, 1, N'FREE',        N'미케비치',      N'자유 일정 및 해변 휴식', 0, 0),
        (N'DAD501', 5, 1, N'FLIGHT',      N'다낭 → 인천',   N'공항 이동 후 귀국', 0, 0);

    INSERT dbo.product_optional_tour (product_code, seq, name, place_name, name_en, summary, details, currency, price_adult, price_child, min_participants, duration_minutes, included, pre_sale) VALUES
        (N'DAD501', 1, N'호이안 야간 선상 투어', N'호이안', N'Hoi An Night Boat', N'소원등 띄우기', N'투본강 선상에서 야경 감상 (샘플)', N'USD', 25, 20, 4, 60, 0, 1),
        (N'DAD501', 2, N'전신 마사지 90분',      N'다낭',   N'Full Body Massage', N'전통 마사지', N'90분 코스 (샘플)', N'USD', 40, 40, 2, 90, 0, 0);

    INSERT dbo.product_shopping (product_code, seq, item_name, place_name, duration_minutes, refundable) VALUES
        (N'DAD501', 1, N'침향', N'다낭 쇼핑센터', 90, 1);

    INSERT dbo.product_remark (product_code, price_note, inclusions, exclusions, highlights, booking_notice, room_info, travel_notice, faq) VALUES
        (N'DAD501', N'아동/유아 요금은 만 나이 기준입니다. (샘플)', N'왕복 항공권, 호텔 4박, 일정상 식사, 전용 차량',
         N'개인 경비, 가이드/기사 팁, 선택관광', N'5성급 리조트 4박, 바나힐 포함',
         N'예약 후 24시간 이내 결제가 필요합니다. (샘플)', N'트윈 2인 1실 기준', N'여권 유효기간 6개월 이상 필요',
         N'Q. 비자가 필요한가요? A. 15일 이내 무비자 입국 가능 (샘플)');

    INSERT dbo.product_condition (product_code, airline_seat_class, tour_leader, local_guide, insurance_included, shopping_count,
        optional_tour_included, admission_fee_included, local_expenses_currency, local_expenses_adult, local_expenses_child,
        local_expenses_infant, single_room_fee, min_departure_population, cancellation_policy) VALUES
        (N'DAD501', N'일반석', 0, N'현지 한국어 가이드', 1, 1, 0, 1, N'USD', 60, 40, 0, 250000, 6,
         N'출발 30일 전까지 무료 취소, 이후 약관에 따른 취소료 부과 (샘플)');
END
GO
