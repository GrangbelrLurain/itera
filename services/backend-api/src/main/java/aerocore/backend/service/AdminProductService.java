package aerocore.backend.service;

import aerocore.backend.dto.AdminProductDtos.ConditionInput;
import aerocore.backend.dto.AdminProductDtos.DepartureItem;
import aerocore.backend.dto.AdminProductDtos.ExtrasInput;
import aerocore.backend.dto.AdminProductDtos.ItineraryInput;
import aerocore.backend.dto.AdminProductDtos.OptionalTourInput;
import aerocore.backend.dto.AdminProductDtos.PriceInput;
import aerocore.backend.dto.AdminProductDtos.ProductDetail;
import aerocore.backend.dto.AdminProductDtos.ProductInput;
import aerocore.backend.dto.AdminProductDtos.ProductSummary;
import aerocore.backend.dto.AdminProductDtos.RemarkInput;
import aerocore.backend.dto.AdminProductDtos.RuleInput;
import aerocore.backend.dto.AdminProductDtos.ScheduleInput;
import aerocore.backend.dto.AdminProductDtos.ShoppingInput;
import aerocore.backend.exception.OperationNotAllowedException;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ProductExistsException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;
import aerocore.backend.model.ProductData.Product;
import aerocore.backend.repository.ProductAdminRepository;
import aerocore.backend.repository.ProductRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Back-office management of master products: basic data and prices, itinerary, departure rules (the schedule),
 * extras, and per-date departure actions. Rules: docs/design/package-departure.md.
 * Product codes are immutable. All validation happens here; repositories only persist.
 */
public class AdminProductService {

    static final int MAX_RULES = 10;
    static final int MAX_EXCLUSIONS = 400;
    static final int MAX_RULE_SPAN_DAYS = 1100;
    static final int MAX_ITINERARY_ROWS = 200;
    static final int MAX_EXTRAS_ROWS = 100;
    static final int MAX_CAPACITY = 999;

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,19}$");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");
    private static final Set<String> TARGETS = Set.of("ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT");
    private static final Set<String> SERVICE_TYPES = Set.of("FLIGHT", "SIGHTSEEING", "MEAL", "HOTEL", "FREE");

    private final ProductRepository read;
    private final ProductAdminRepository write;
    private final Clock clock;

    public AdminProductService(ProductRepository read, ProductAdminRepository write, Clock clock) {
        this.read = read;
        this.write = write;
        this.clock = clock;
    }

    // ---- products --------------------------------------------------------------------------

    public List<ProductSummary> listProducts() {
        List<ProductSummary> out = new ArrayList<>();
        for (ProductData d : read.findAllProductSummaries()) {
            Product p = d.product();
            out.add(new ProductSummary(p.code(), p.name(), p.grade(), p.nights(), p.days(), p.active(),
                    p.defaultCapacity(), scheduleSummary(d)));
        }
        return out;
    }

    public ProductDetail getProduct(String code) {
        ProductData d = read.findProduct(code).orElseThrow(() -> new PackageNotFoundException(code, null));
        Product p = d.product();
        ProductInput product = new ProductInput(p.code(), p.name(), p.grade(), p.nights(), p.days(),
                p.departureRoute(), p.departureTime(), p.returnRoute(), p.returnTime(), p.directFlight(),
                p.visaRequired(), p.meetingTime(), p.meetingLocation(), p.hotelName(), p.hotelStars(), p.highlight(),
                p.promotionTitle(), p.discountAmount(), p.defaultCapacity(), p.active(),
                d.prices().stream().map(x -> new PriceInput(x.targetType(), x.basePrice(), x.fuelSurcharge(),
                        x.taxesAndCharges(), x.salesAvailable())).toList());

        List<RuleInput> rules = d.rules().stream().map(r -> new RuleInput(r.validFrom().toString(),
                r.validTo().toString(), r.weekdays().stream().sorted().map(w -> w.name().substring(0, 3)).toList(),
                r.active())).toList();
        List<String> exclusions = new TreeSet<>(d.exclusions().stream().map(LocalDate::toString).toList())
                .stream().toList();

        List<ItineraryInput> itinerary = d.itinerary().stream().map(r -> new ItineraryInput(r.dayNo(),
                r.serviceType(), r.placeName(), r.description(), r.highlight(), r.perk())).toList();

        ProductData.Remark r = d.remark();
        ProductData.Condition c = d.condition();
        ExtrasInput extras = new ExtrasInput(
                d.optionalTours().stream().map(t -> new OptionalTourInput(t.name(), t.placeName(), t.nameEn(),
                        t.summary(), t.details(), t.currency(), t.priceAdult(), t.priceChild(), t.minParticipants(),
                        t.durationMinutes(), t.included(), t.preSale())).toList(),
                d.shoppings().stream().map(s -> new ShoppingInput(s.itemName(), s.placeName(), s.durationMinutes(),
                        s.refundable())).toList(),
                r == null ? null : new RemarkInput(r.priceNote(), r.inclusions(), r.exclusions(), r.highlights(),
                        r.bookingNotice(), r.roomInfo(), r.travelNotice(), r.faq()),
                c == null ? null : new ConditionInput(c.airlineSeatClass(), c.tourLeader(), c.localGuide(),
                        c.insuranceIncluded(), c.shoppingCount(), c.optionalTourIncluded(), c.admissionFeeIncluded(),
                        c.localExpensesCurrency(), c.localExpensesAdult(), c.localExpensesChild(),
                        c.localExpensesInfant(), c.singleRoomFee(), c.minDeparturePopulation(), c.cancellationPolicy()));

        return new ProductDetail(product, new ScheduleInput(rules, exclusions), itinerary, extras);
    }

    /** Creates the product with its base prices. Rules, itinerary and extras are added afterwards. */
    public ProductDetail createProduct(ProductInput in) {
        if (in == null) throw new ValidationException("body is required");
        String code = in.code() == null ? "" : in.code().trim().toUpperCase();
        if (!CODE.matcher(code).matches()) {
            throw new ValidationException("code must be 2-20 characters of A-Z, 0-9, _ or - (starting with a letter or digit)");
        }
        if (write.exists(code)) throw new ProductExistsException(code);
        Product p = toProduct(code, in);
        write.insertProduct(p, toPrices(in.prices()));
        return getProduct(code);
    }

    public ProductDetail updateProduct(String code, ProductInput in) {
        requireExisting(code);
        if (in == null) throw new ValidationException("body is required");
        if (in.code() != null && !in.code().isBlank() && !in.code().trim().equalsIgnoreCase(code)) {
            throw new ValidationException("the product code cannot be changed");
        }
        write.updateProduct(toProduct(code, in), toPrices(in.prices()));
        return getProduct(code);
    }

    public ProductDetail setActive(String code, boolean active) {
        requireExisting(code);
        write.setActive(code, active); // inactive products stay readable; only new bookings are blocked
        return getProduct(code);
    }

    // ---- itinerary / schedule / extras -----------------------------------------------------

    public ProductDetail replaceItinerary(String code, List<ItineraryInput> rows) {
        ProductData d = requireExisting(code);
        List<ItineraryInput> input = rows == null ? List.of() : rows;
        if (input.size() > MAX_ITINERARY_ROWS) throw new ValidationException("itinerary can have at most " + MAX_ITINERARY_ROWS + " rows");
        int days = d.product().days();
        Map<Integer, Integer> perDay = new TreeMap<>();
        List<ProductData.ItineraryRow> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItineraryInput r = input.get(i);
            String who = "itinerary[" + i + "]";
            if (r == null || r.dayNo() == null || r.dayNo() < 1 || r.dayNo() > days) {
                throw new ValidationException(who + ".dayNo must be between 1 and " + days);
            }
            if (r.serviceType() == null || !SERVICE_TYPES.contains(r.serviceType())) {
                throw new ValidationException(who + ".serviceType must be one of " + SERVICE_TYPES);
            }
            String place = text(r.placeName(), 200, who + ".placeName");
            String desc = text(r.description(), 4000, who + ".description");
            if (place == null && desc == null) throw new ValidationException(who + " needs a place or a description");
            int sort = perDay.merge(r.dayNo(), 1, Integer::sum); // order inside a day follows the input order
            out.add(new ProductData.ItineraryRow(r.dayNo(), sort, r.serviceType(), place, desc,
                    Boolean.TRUE.equals(r.highlight()), Boolean.TRUE.equals(r.perk())));
        }
        write.replaceItinerary(code, out);
        return getProduct(code);
    }

    public ProductDetail replaceSchedule(String code, ScheduleInput in) {
        requireExisting(code);
        List<RuleInput> rules = in == null || in.rules() == null ? List.of() : in.rules();
        List<String> excl = in == null || in.exclusions() == null ? List.of() : in.exclusions();
        if (rules.size() > MAX_RULES) throw new ValidationException("at most " + MAX_RULES + " rules are allowed");
        if (excl.size() > MAX_EXCLUSIONS) throw new ValidationException("at most " + MAX_EXCLUSIONS + " excluded dates are allowed");

        List<ProductData.Rule> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            RuleInput r = rules.get(i);
            String who = "rules[" + i + "]";
            if (r == null) throw new ValidationException(who + " is required");
            LocalDate from = date(r.validFrom(), who + ".validFrom");
            LocalDate to = date(r.validTo(), who + ".validTo");
            if (to.isBefore(from)) throw new ValidationException(who + ": validTo must not be before validFrom");
            if (from.plusDays(MAX_RULE_SPAN_DAYS).isBefore(to)) {
                throw new ValidationException(who + ": a rule can span at most " + MAX_RULE_SPAN_DAYS + " days");
            }
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (String w : r.weekdays() == null ? List.<String>of() : r.weekdays()) days.add(weekday(w, who));
            if (days.isEmpty()) throw new ValidationException(who + ".weekdays needs at least one weekday");
            out.add(new ProductData.Rule(0, from, to, days, !Boolean.FALSE.equals(r.active())));
        }
        Set<LocalDate> exclusions = new LinkedHashSet<>();
        for (int i = 0; i < excl.size(); i++) exclusions.add(date(excl.get(i), "exclusions[" + i + "]"));

        write.replaceSchedule(code, out, exclusions);
        return getProduct(code);
    }

    public ProductDetail replaceExtras(String code, ExtrasInput in) {
        requireExisting(code);
        ExtrasInput e = in == null ? new ExtrasInput(null, null, null, null) : in;
        List<OptionalTourInput> tours = e.optionalTours() == null ? List.of() : e.optionalTours();
        List<ShoppingInput> shoppings = e.shoppings() == null ? List.of() : e.shoppings();
        if (tours.size() > MAX_EXTRAS_ROWS || shoppings.size() > MAX_EXTRAS_ROWS) {
            throw new ValidationException("at most " + MAX_EXTRAS_ROWS + " tours and shopping stops are allowed");
        }

        List<ProductData.OptionalTourRow> tourRows = new ArrayList<>();
        for (int i = 0; i < tours.size(); i++) {
            OptionalTourInput t = tours.get(i);
            String who = "optionalTours[" + i + "]";
            String name = required(t == null ? null : t.name(), 200, who + ".name");
            String currency = t.currency() == null || t.currency().isBlank() ? null : t.currency().trim().toUpperCase();
            if (currency != null && !CURRENCY.matcher(currency).matches()) {
                throw new ValidationException(who + ".currency must be a 3-letter code");
            }
            nonNegative(t.priceAdult(), who + ".priceAdult");
            nonNegative(t.priceChild(), who + ".priceChild");
            nonNegative(t.minParticipants(), who + ".minParticipants");
            nonNegative(t.durationMinutes(), who + ".durationMinutes");
            tourRows.add(new ProductData.OptionalTourRow(i + 1, name, text(t.placeName(), 200, who + ".placeName"),
                    text(t.nameEn(), 200, who + ".nameEn"), text(t.summary(), 500, who + ".summary"),
                    text(t.details(), 8000, who + ".details"), currency, t.priceAdult(), t.priceChild(),
                    t.minParticipants(), t.durationMinutes(), t.included(), t.preSale()));
        }
        List<ProductData.ShoppingRow> shopRows = new ArrayList<>();
        for (int i = 0; i < shoppings.size(); i++) {
            ShoppingInput s = shoppings.get(i);
            String who = "shoppings[" + i + "]";
            String name = required(s == null ? null : s.itemName(), 200, who + ".itemName");
            nonNegative(s.durationMinutes(), who + ".durationMinutes");
            shopRows.add(new ProductData.ShoppingRow(i + 1, name, text(s.placeName(), 200, who + ".placeName"),
                    s.durationMinutes(), Boolean.TRUE.equals(s.refundable())));
        }

        RemarkInput r = e.remark();
        ProductData.Remark remark = r == null ? null : new ProductData.Remark(
                text(r.priceNote(), 8000, "remark.priceNote"), text(r.inclusions(), 8000, "remark.inclusions"),
                text(r.exclusions(), 8000, "remark.exclusions"), text(r.highlights(), 8000, "remark.highlights"),
                text(r.bookingNotice(), 8000, "remark.bookingNotice"), text(r.roomInfo(), 8000, "remark.roomInfo"),
                text(r.travelNotice(), 8000, "remark.travelNotice"), text(r.faq(), 8000, "remark.faq"));

        ConditionInput c = e.condition();
        ProductData.Condition condition = null;
        if (c != null) {
            String currency = c.localExpensesCurrency() == null || c.localExpensesCurrency().isBlank()
                    ? null : c.localExpensesCurrency().trim().toUpperCase();
            if (currency != null && !CURRENCY.matcher(currency).matches()) {
                throw new ValidationException("condition.localExpensesCurrency must be a 3-letter code");
            }
            condition = new ProductData.Condition(text(c.airlineSeatClass(), 30, "condition.airlineSeatClass"),
                    Boolean.TRUE.equals(c.tourLeader()), text(c.localGuide(), 50, "condition.localGuide"),
                    Boolean.TRUE.equals(c.insuranceIncluded()), orZero(c.shoppingCount(), "condition.shoppingCount"),
                    c.optionalTourIncluded(), Boolean.TRUE.equals(c.admissionFeeIncluded()), currency,
                    orZero(c.localExpensesAdult(), "condition.localExpensesAdult"),
                    orZero(c.localExpensesChild(), "condition.localExpensesChild"),
                    orZero(c.localExpensesInfant(), "condition.localExpensesInfant"),
                    orZero(c.singleRoomFee(), "condition.singleRoomFee"),
                    orZero(c.minDeparturePopulation(), "condition.minDeparturePopulation"),
                    text(c.cancellationPolicy(), 8000, "condition.cancellationPolicy"));
        }

        write.replaceExtras(code, tourRows, shopRows, remark, condition);
        return getProduct(code);
    }

    // ---- departures ------------------------------------------------------------------------

    /**
     * Departure calendar of a product between {@code from} and {@code to} (defaults: tomorrow .. 12 months).
     * Includes canceled stored dates so they can be reopened.
     */
    public List<DepartureItem> departures(String code, LocalDate from, LocalDate to) {
        ProductData d = requireExisting(code);
        Map<LocalDate, DepartureRow> rows = read.findDepartureRows(code);
        LocalDate today = BusinessTime.today(clock);
        LocalDate start = from == null || !from.isAfter(today) ? today.plusDays(1) : from;
        LocalDate end = to == null ? start.plusMonths(12) : to;
        if (end.isAfter(start.plusDays(PackageService.MAX_HORIZON_DAYS))) end = start.plusDays(PackageService.MAX_HORIZON_DAYS);

        TreeMap<LocalDate, DepartureItem> out = new TreeMap<>();
        for (LocalDate date : DepartureCalculator.dates(d, rows, start, end, today)) {
            out.put(date, item(d, rows.get(date), date));
        }
        for (DepartureRow r : rows.values()) { // canceled rows are not part of V but must stay manageable
            if (r.status() == DepartureRow.Status.CANCELED && !r.date().isBefore(start) && !r.date().isAfter(end)) {
                out.put(r.date(), item(d, r, r.date()));
            }
        }
        return new ArrayList<>(out.values());
    }

    public DepartureItem closeDeparture(String code, LocalDate date) {
        ProductData d = requireDeparture(code, date);
        write.closeDeparture(code, date, d.product().defaultCapacity());
        return after(code, date);
    }

    public DepartureItem reopenDeparture(String code, LocalDate date) {
        ProductData d = requireExisting(code);
        DepartureRow row = read.findDepartureRows(code).get(date);
        if (row == null || row.status() == DepartureRow.Status.OPEN) {
            throw new OperationNotAllowedException("This departure is not closed or canceled");
        }
        if (!date.isAfter(BusinessTime.today(clock))) throw new OperationNotAllowedException("This departure is in the past");
        write.reopenDeparture(code, date);
        return after(code, date);
    }

    public DepartureItem setCapacity(String code, LocalDate date, Integer capacity) {
        ProductData d = requireDeparture(code, date);
        if (capacity == null || capacity < 1 || capacity > MAX_CAPACITY) {
            throw new ValidationException("capacity must be between 1 and " + MAX_CAPACITY);
        }
        if (!write.setCapacity(code, date, capacity, d.product().defaultCapacity())) {
            DepartureRow row = read.findDepartureRows(code).get(date);
            int reserved = row == null ? 0 : row.reservedSeats();
            throw new OperationNotAllowedException(
                    "Capacity cannot be lower than the " + reserved + " seats already reserved");
        }
        return after(code, date);
    }

    public DepartureItem cancelDeparture(String code, LocalDate date) {
        ProductData d = requireDeparture(code, date);
        if (!write.cancelDeparture(code, date, d.product().defaultCapacity())) {
            throw new OperationNotAllowedException("This departure has bookings; cancel the bookings first");
        }
        return after(code, date);
    }

    /** Adds a one-off date outside the rules (storage rule S3). */
    public DepartureItem addDeparture(String code, LocalDate date) {
        ProductData d = requireExisting(code);
        LocalDate today = BusinessTime.today(clock);
        if (date == null || !date.isAfter(today)) throw new ValidationException("the date must be after today");
        if (date.isAfter(today.plusDays(PackageService.MAX_HORIZON_DAYS))) {
            throw new ValidationException("the date is too far ahead (max " + PackageService.MAX_HORIZON_DAYS + " days)");
        }
        DepartureRow row = read.findDepartureRows(code).get(date);
        if (row != null && row.status() == DepartureRow.Status.CANCELED) {
            throw new OperationNotAllowedException("This date was canceled; reopen it instead");
        }
        write.addDeparture(code, date, d.product().defaultCapacity());
        return after(code, date);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private ProductData requireExisting(String code) {
        return read.findProduct(code).orElseThrow(() -> new PackageNotFoundException(code, null));
    }

    /** The date must currently be a departure of the product (rule date or stored/pinned, not canceled). */
    private ProductData requireDeparture(String code, LocalDate date) {
        ProductData d = requireExisting(code);
        if (date == null || !DepartureCalculator.isDeparture(d, read.findDepartureRows(code), date,
                BusinessTime.today(clock))) {
            throw new PackageNotFoundException(code, date);
        }
        return d;
    }

    private DepartureItem after(String code, LocalDate date) {
        ProductData d = requireExisting(code);
        return item(d, read.findDepartureRows(code).get(date), date);
    }

    private static DepartureItem item(ProductData d, DepartureRow row, LocalDate date) {
        String status;
        int capacity;
        int reserved;
        if (row != null && row.status() == DepartureRow.Status.CANCELED) {
            status = "CANCELED";
            capacity = row.capacity();
            reserved = row.reservedSeats();
        } else {
            DepartureCalculator.Effective e = DepartureCalculator.effective(d, row, date);
            status = e.status().name();
            capacity = e.capacity();
            reserved = e.reservedSeats();
        }
        int overrides = d.pricing() == null ? 0 : d.pricing().overrides().getOrDefault(date, List.of()).size();
        return new DepartureItem(date.toString(), date.getDayOfWeek().name().substring(0, 3), status, capacity,
                reserved, Math.max(0, capacity - reserved), row != null, row != null && row.pinned(),
                DepartureCalculator.ruleProduces(d, date), overrides);
    }

    private static String scheduleSummary(ProductData d) {
        List<String> parts = new ArrayList<>();
        for (ProductData.Rule r : d.rules()) {
            String days = r.weekdays().stream().sorted()
                    .map(w -> w.getDisplayName(TextStyle.SHORT, Locale.KOREAN)).reduce((a, b) -> a + "·" + b).orElse("");
            parts.add(days + " " + r.validFrom() + "~" + r.validTo() + (r.active() ? "" : " (중지)"));
        }
        return parts.isEmpty() ? "규칙 없음" : String.join(", ", parts);
    }

    private Product toProduct(String code, ProductInput in) {
        String name = required(in.name(), 100, "name");
        int nights = range(in.nights(), 0, 60, "nights");
        int days = range(in.days(), 1, 61, "days");
        if (days < nights) throw new ValidationException("days cannot be smaller than nights");
        long discount = in.discountAmount() == null ? 0 : in.discountAmount();
        if (discount < 0) throw new ValidationException("discountAmount cannot be negative");
        Integer stars = in.hotelStars();
        if (stars != null && (stars < 1 || stars > 5)) throw new ValidationException("hotelStars must be 1-5");
        return new Product(code, name, text(in.grade(), 30, "grade"), nights, days,
                text(in.departureRoute(), 20, "departureRoute"), time(in.departureTime(), "departureTime"),
                text(in.returnRoute(), 20, "returnRoute"), time(in.returnTime(), "returnTime"),
                Boolean.TRUE.equals(in.directFlight()), Boolean.TRUE.equals(in.visaRequired()),
                time(in.meetingTime(), "meetingTime"), text(in.meetingLocation(), 100, "meetingLocation"),
                text(in.hotelName(), 200, "hotelName"), stars, text(in.highlight(), 500, "highlight"),
                text(in.promotionTitle(), 200, "promotionTitle"), discount,
                range(in.defaultCapacity(), 1, MAX_CAPACITY, "defaultCapacity"), !Boolean.FALSE.equals(in.active()));
    }

    private static List<ProductData.Price> toPrices(List<PriceInput> prices) {
        List<PriceInput> in = prices == null ? List.of() : prices;
        List<ProductData.Price> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < in.size(); i++) {
            PriceInput p = in.get(i);
            String who = "prices[" + i + "]";
            if (p == null || p.targetType() == null || !TARGETS.contains(p.targetType())) {
                throw new ValidationException(who + ".targetType must be one of " + TARGETS);
            }
            if (!seen.add(p.targetType())) throw new ValidationException(who + ": duplicate " + p.targetType());
            long base = money(p.basePrice(), who + ".basePrice", true);
            out.add(new ProductData.Price(p.targetType(), base, money(p.fuelSurcharge(), who + ".fuelSurcharge", false),
                    money(p.taxesAndCharges(), who + ".taxesAndCharges", false),
                    !Boolean.FALSE.equals(p.salesAvailable())));
        }
        if (!seen.contains("ADULT")) throw new ValidationException("an ADULT price is required");
        return out;
    }

    private static long money(Long v, String field, boolean required) {
        if (v == null) {
            if (required) throw new ValidationException(field + " is required");
            return 0;
        }
        if (v < 0 || v > 1_000_000_000L) throw new ValidationException(field + " must be between 0 and 1,000,000,000");
        return v;
    }

    private static int range(Integer v, int min, int max, String field) {
        if (v == null || v < min || v > max) throw new ValidationException(field + " must be between " + min + " and " + max);
        return v;
    }

    private static int orZero(Integer v, String field) {
        if (v == null) return 0;
        if (v < 0) throw new ValidationException(field + " cannot be negative");
        return v;
    }

    private static void nonNegative(Number v, String field) {
        if (v != null && v.doubleValue() < 0) throw new ValidationException(field + " cannot be negative");
    }

    private static String required(String v, int max, String field) {
        String t = text(v, max, field);
        if (t == null) throw new ValidationException(field + " is required");
        return t;
    }

    /** Trims; blank becomes null; too long is rejected. */
    private static String text(String v, int max, String field) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty()) return null;
        if (t.length() > max) throw new ValidationException(field + " is too long (max " + max + ")");
        return t;
    }

    private static String time(String v, String field) {
        String t = text(v, 5, field);
        if (t != null && !TIME.matcher(t).matches()) throw new ValidationException(field + " must be HH:mm");
        return t;
    }

    private static LocalDate date(String v, String field) {
        try {
            return LocalDate.parse(v == null ? "" : v.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be yyyy-MM-dd");
        }
    }

    private static DayOfWeek weekday(String s, String who) {
        if (s != null && s.trim().length() >= 3) {
            String prefix = s.trim().toUpperCase().substring(0, 3);
            for (DayOfWeek d : DayOfWeek.values()) if (d.name().startsWith(prefix)) return d;
        }
        throw new ValidationException(who + ".weekdays contains an unknown weekday: " + s);
    }
}
