package aerocore.backend.config;

import aerocore.backend.model.ChannelCommission;
import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Condition;
import aerocore.backend.model.ProductData.CostRow;
import aerocore.backend.model.ProductData.ItineraryRow;
import aerocore.backend.model.ProductData.OptionalTourRow;
import aerocore.backend.model.ProductData.Price;
import aerocore.backend.model.ProductData.Pricing;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.model.ProductData.Remark;
import aerocore.backend.model.ProductData.Rule;
import aerocore.backend.model.ProductData.ShoppingRow;
import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.repository.memory.MemoryCommissionRepository;
import aerocore.backend.repository.memory.MemoryProductStore;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The state of the memory profile: sample products, commissions and no bookings. Dates are relative to the
 * business day (Seoul), so the sample departures never run out of the calendar window.
 */
public class MemoryData {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final MemoryProductStore products;
    private final MemoryCommissionRepository commissions;
    private final InMemoryPackageBookingRepository bookings;
    private final Clock clock;

    public MemoryData(MemoryProductStore products, MemoryCommissionRepository commissions,
                      InMemoryPackageBookingRepository bookings, Clock clock) {
        this.products = products;
        this.commissions = commissions;
        this.bookings = bookings;
        this.clock = clock;
    }

    /** Drops everything and loads the sample data again. */
    public synchronized void reset() {
        products.clear();
        bookings.clear();
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate until = today.plusYears(1);

        products.put(danang(today, until)).put(tokyo(today, until));
        commissions.replaceAll(List.of(
                new ChannelCommission("AGENCY_A", null, 800, 0, true),
                new ChannelCommission("AGENCY_A", "DAD501", 1000, 0, true),
                new ChannelCommission("PARTNER_C", null, 0, 20000, true)));
    }

    private static Rule rule(LocalDate from, LocalDate to, DayOfWeek... days) {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        set.addAll(List.of(days));
        return new Rule(1, from, to, set, true);
    }

    /** Cost-priced premium package (adult 1,030,000 cost + 25 % margin = 1,287,500). */
    private static ProductData danang(LocalDate from, LocalDate until) {
        Product p = new Product("DAD501", "프리미엄 다낭 호이안 4박5일 (샘플)", "프리미엄", 4, 5, "ICN-DAD", "09:30",
                "DAD-ICN", "20:10", true, false, "07:30", "인천공항 제1터미널", "다낭 리조트 (샘플)", 5,
                "동급 상품 대비 쇼핑 1회, 전 일정 5성급 호텔 (샘플 문구)", "얼리버드 특가", 50000, 30, true);
        List<Price> prices = List.of(
                new Price("ADULT", 1287500, 90000, 45000, true),
                new Price("NOBED_CHILD", 1162500, 90000, 45000, true),
                new Price("EXTRABED_CHILD", 1190000, 90000, 45000, true),
                new Price("INFANT", 100000, 0, 12000, true));
        List<CostRow> costs = List.of(
                new CostRow(1, "FLIGHT", "ADULT", 520000, "왕복 항공"),
                new CostRow(2, "HOTEL", "ADULT", 300000, "4박 호텔"),
                new CostRow(3, "LAND", "ADULT", 150000, "차량·가이드"),
                new CostRow(4, "TOUR", "ADULT", 60000, "입장료"));
        List<ItineraryRow> itinerary = List.of(
                new ItineraryRow(1, 1, "FLIGHT", "인천 → 다낭", "인천공항 출발, 다낭 도착 후 호텔 이동", false, false),
                new ItineraryRow(1, 2, "HOTEL", "다낭 리조트", "호텔 체크인 및 휴식", false, false),
                new ItineraryRow(2, 1, "SIGHTSEEING", "호이안 구시가지", "도보 관광", true, false),
                new ItineraryRow(5, 1, "FLIGHT", "다낭 → 인천", "귀국", false, false));
        List<OptionalTourRow> tours = List.of(new OptionalTourRow(1, "바나힐 (샘플)", "다낭", null, "케이블카와 정원",
                null, "USD", 45.0, 35.0, 4, 240, false, false));
        List<ShoppingRow> shoppings = List.of(new ShoppingRow(1, "커피·잡화 (샘플)", "다낭", 60, true));
        Remark remark = new Remark("유류할증료와 제세공과금이 포함된 금액입니다. (샘플)", "항공권, 숙박, 일정표의 식사",
                "개인 경비", "5성급 리조트", "여권 유효기간 6개월 이상", "트윈 기준", "현지 사정에 따라 일정이 바뀔 수 있습니다.",
                null);
        Condition condition = new Condition("일반석", true, "한국어 가이드", true, 1, false, true, "USD", 40, 20, 0,
                150000, 10, "출발 30일 전까지 무료 취소 (샘플)");
        return new ProductData(p, prices, List.of(rule(from, until, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)),
                Set.of(), itinerary, tours, shoppings, remark, condition, new Pricing(costs, 2500, 0));
    }

    /** Manually priced economy package. */
    private static ProductData tokyo(LocalDate from, LocalDate until) {
        Product p = new Product("TYO201", "실속 도쿄 자유여행 3박4일 (샘플)", "실속", 3, 4, "ICN-NRT", "08:00", "NRT-ICN",
                "21:00", true, false, "06:00", "인천공항 제2터미널", "도쿄 시내 호텔 (샘플)", 3, "자유 일정 중심 (샘플 문구)",
                null, 0, 40, true);
        List<Price> prices = List.of(
                new Price("ADULT", 898000, 60000, 40000, true),
                new Price("INFANT", 100000, 0, 10000, true));
        return new ProductData(p, prices, List.of(rule(from, until, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)), Set.of(),
                List.of(new ItineraryRow(1, 1, "FLIGHT", "인천 → 도쿄", "출발", false, false)), List.of(), List.of(),
                null, null);
    }
}
