package aerocore.backend.service;

import aerocore.backend.dto.PackageDetailResponse;
import aerocore.backend.dto.PackageSummaryResponse;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.repository.ProductRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Package list/detail. A package is a product on a departure date; dates come from DepartureCalculator
 * (rules + stored rows), prices from the PriceCalculator.
 */
public class PackageService {

    /** How far ahead virtual dates are generated when the caller gives no upper bound. */
    static final int MAX_HORIZON_DAYS = 730;
    static final int MAX_PAGE_SIZE = 100;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YY_MM_DD = DateTimeFormatter.ofPattern("yy-MM-dd");

    private final ProductRepository repository;
    private final PriceCalculator prices;
    private final Clock clock;

    public PackageService(ProductRepository repository) {
        this(repository, new CostBasedPriceCalculator(), Clock.systemUTC());
    }

    public PackageService(ProductRepository repository, PriceCalculator prices, Clock clock) {
        this.repository = repository;
        this.prices = prices;
        this.clock = clock;
    }

    // ---- list ----------------------------------------------------------------------------------

    public PackageSummaryResponse.Page listPackages(PackageSearch search, int page, int size) {
        PackageSearch s = search == null ? PackageSearch.none() : search;
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        LocalDate today = BusinessTime.today(clock);
        LocalDate from = s.from() != null ? s.from() : today.plusDays(1);
        LocalDate horizon = today.plusDays(MAX_HORIZON_DAYS);
        LocalDate to = s.to() != null && s.to().isBefore(horizon) ? s.to() : horizon;

        Map<String, Map<LocalDate, DepartureRow>> allRows = repository.findAllDepartureRows();
        List<Row> rows = new ArrayList<>();
        for (ProductData data : repository.findActiveProductSummaries()) {
            Product p = data.product();
            if (!matchesKeyword(p, s.keyword())) continue;
            Map<LocalDate, DepartureRow> stored = allRows.getOrDefault(p.code(), Map.of());
            for (LocalDate date : DepartureCalculator.dates(data, stored, from, to, today)) {
                if (!s.weekdays().isEmpty() && !s.weekdays().contains(date.getDayOfWeek())) continue;
                DepartureCalculator.Effective e = DepartureCalculator.effective(data, stored.get(date), date);
                if (!s.statuses().isEmpty() && !s.statuses().contains(e.status().name().toLowerCase())) continue;
                rows.add(new Row(data, e));
            }
        }
        rows.sort(Comparator.<Row, LocalDate>comparing(r -> r.eff.date()).thenComparing(r -> r.data.product().code()));

        int fromIdx = Math.min(safePage * safeSize, rows.size());
        int toIdx = Math.min(fromIdx + safeSize, rows.size());
        List<PackageSummaryResponse> items = new ArrayList<>();
        for (Row r : rows.subList(fromIdx, toIdx)) {
            Product p = r.data.product();
            PriceCalculator.Quote adult = prices.quote(r.data, r.eff.date(), "ADULT");
            items.add(new PackageSummaryResponse(p.code(), r.eff.date().format(ISO), displayName(p), p.nights(),
                    p.days(), r.eff.status().name(), r.eff.remainingSeats(),
                    adult != null ? adult.totalBeforeDiscount() : 0, adult != null ? adult.payable() : 0));
        }
        return new PackageSummaryResponse.Page(items, rows.size(), safePage, safeSize);
    }

    private record Row(ProductData data, DepartureCalculator.Effective eff) {}

    /**
     * Departures that are nearly sold out (remaining <= threshold) and still open.
     * Only stored rows can be low: a date without a row has its full default capacity.
     */
    public List<LowSeat> lowSeatDepartures(int threshold) {
        LocalDate today = BusinessTime.today(clock);
        Map<String, ProductData> products = new TreeMap<>();
        repository.findActiveProductSummaries().forEach(d -> products.put(d.product().code(), d));
        List<LowSeat> out = new ArrayList<>();
        repository.findAllDepartureRows().forEach((code, rows) -> {
            ProductData data = products.get(code);
            if (data == null) return;
            rows.values().stream()
                    .filter(r -> DepartureCalculator.isDeparture(data, rows, r.date(), today))
                    .filter(r -> r.status() == DepartureRow.Status.OPEN && r.remainingSeats() <= threshold)
                    .forEach(r -> out.add(new LowSeat(code, displayName(data.product()), r.date().format(ISO),
                            r.remainingSeats())));
        });
        out.sort(Comparator.comparing(LowSeat::departureDate).thenComparing(LowSeat::productCode));
        return out;
    }

    public record LowSeat(String productCode, String name, String departureDate, int remainingSeats) {}

    // ---- detail --------------------------------------------------------------------------------

    public PackageDetailResponse getDetail(String productCode, LocalDate date) {
        ProductData data = repository.findProduct(productCode)
                .orElseThrow(() -> new PackageNotFoundException(productCode, date));
        Map<LocalDate, DepartureRow> rows = repository.findDepartureRows(productCode);
        LocalDate today = BusinessTime.today(clock);
        if (!data.product().active() || !DepartureCalculator.isDeparture(data, rows, date, today)) {
            throw new PackageNotFoundException(productCode, date);
        }
        Product p = data.product();
        DepartureCalculator.Effective e = DepartureCalculator.effective(data, rows.get(date), date);

        PackageDetailResponse.Basic basic = new PackageDetailResponse.Basic(
                p.code(), date.format(ISO), displayName(p), p.grade(), p.promotionTitle(),
                e.status().name(), e.stored(),
                p.departureRoute() + " " + p.departureTime(),
                date.plusDays(p.days() - 1L).format(YY_MM_DD) + " [" + p.returnRoute() + " " + p.returnTime() + "]",
                p.nights(), p.days(), p.directFlight(), p.visaRequired(),
                meetingTime(p, date), p.meetingLocation(), p.hotelName(), p.hotelStars(),
                date.minusDays(30).format(ISO),
                e.capacity(), e.reservedSeats(), e.remainingSeats(),
                p.highlight());

        List<PackageDetailResponse.Price> priceRows = new ArrayList<>();
        for (ProductData.Price price : data.prices()) {
            PriceCalculator.Quote q = prices.quote(data, date, price.targetType());
            if (q == null) continue;
            priceRows.add(new PackageDetailResponse.Price(q.targetType(), q.salePrice(), q.discount(),
                    q.fuelSurcharge(), q.taxesAndCharges(), q.totalBeforeDiscount(), q.payable(), q.salesAvailable()));
        }

        List<PackageDetailResponse.OptionalTour> tours = data.optionalTours().stream()
                .map(t -> new PackageDetailResponse.OptionalTour(t.seq(), t.name(), t.placeName(), t.nameEn(),
                        t.summary(), t.details(), t.currency(), t.priceAdult(), t.priceChild(), t.minParticipants(),
                        t.durationMinutes(), t.included(), t.preSale()))
                .toList();

        List<PackageDetailResponse.Shopping> shoppings = data.shoppings().stream()
                .map(s -> new PackageDetailResponse.Shopping(s.seq(), s.itemName(), s.placeName(),
                        s.durationMinutes(), s.refundable()))
                .toList();

        // Itinerary rows are stored by day number; the calendar date is derived from the departure date.
        TreeMap<Integer, List<PackageDetailResponse.Item>> byDay = new TreeMap<>();
        for (ProductData.ItineraryRow r : data.itinerary()) {
            byDay.computeIfAbsent(r.dayNo(), k -> new ArrayList<>()).add(new PackageDetailResponse.Item(
                    r.serviceType(), r.placeName(), r.description(), r.perk(), r.highlight()));
        }
        List<PackageDetailResponse.Day> days = new ArrayList<>();
        byDay.forEach((day, items) -> days.add(
                new PackageDetailResponse.Day(day, date.plusDays(day - 1L).format(ISO), items)));

        ProductData.Remark r = data.remark();
        PackageDetailResponse.Remark remark = r == null ? null : new PackageDetailResponse.Remark(
                r.priceNote(), r.inclusions(), r.exclusions(), r.highlights(), r.bookingNotice(), r.roomInfo(),
                r.travelNotice(), r.faq());

        ProductData.Condition c = data.condition();
        PackageDetailResponse.Condition condition = c == null ? null : new PackageDetailResponse.Condition(
                c.airlineSeatClass(), c.tourLeader(), c.localGuide(), c.insuranceIncluded(), c.shoppingCount(),
                c.optionalTourIncluded(), c.admissionFeeIncluded(), c.localExpensesCurrency(),
                c.localExpensesAdult(), c.localExpensesChild(), c.localExpensesInfant(), c.singleRoomFee(),
                c.minDeparturePopulation(), c.cancellationPolicy());

        return new PackageDetailResponse(basic, priceRows, tours, shoppings, new PackageDetailResponse.Itinerary(days),
                remark, condition);
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** "◆grade◆name" (the grade part only when the product has a grade). */
    static String displayName(Product p) {
        return p.grade() == null || p.grade().isBlank() ? p.name() : "◆" + p.grade() + "◆" + p.name();
    }

    /** A meeting time later than the departure time (string compare of HH:mm) means the day before. */
    static String meetingTime(Product p, LocalDate date) {
        if (p.meetingTime() == null) return null;
        if (p.departureTime() != null && p.meetingTime().compareTo(p.departureTime()) > 0) {
            return date.minusDays(1).format(ISO) + " " + p.meetingTime();
        }
        return p.meetingTime();
    }

    private static boolean matchesKeyword(Product p, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        return (displayName(p) + " " + p.code()).toLowerCase().contains(keyword.trim().toLowerCase());
    }
}
