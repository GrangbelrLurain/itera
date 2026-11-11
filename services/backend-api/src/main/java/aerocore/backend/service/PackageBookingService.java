package aerocore.backend.service;

import aerocore.backend.dto.AdminDtos.AdminBookingDetail;
import aerocore.backend.dto.AdminDtos.TravelerFinance;
import aerocore.backend.dto.PackageBookingDtos.Contact;
import aerocore.backend.dto.PackageBookingDtos.CreateRequest;
import aerocore.backend.dto.PackageBookingDtos.Response;
import aerocore.backend.dto.PackageBookingDtos.TravelerInput;
import aerocore.backend.dto.PackageBookingDtos.TravelerView;
import aerocore.backend.exception.PackageBookingNotFoundException;
import aerocore.backend.exception.PackageNotBookableException;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.model.PackageBookingData.Status;
import aerocore.backend.model.PackageBookingData.Traveler;
import aerocore.backend.model.ProductData;
import aerocore.backend.repository.ChannelCommissionRepository;
import aerocore.backend.repository.PackageBookingRepository;
import aerocore.backend.repository.ProductRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Package booking lifecycle: create (seat hold) -> pay -> cancel, with lazy expiry of unpaid bookings.
 * A booking targets a departure = (product code, date). Rules: docs/design/package-booking.md and
 * docs/design/package-departure.md.
 */
public class PackageBookingService {

    static final int MAX_TRAVELERS = 9;
    static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
    private static final Set<String> TARGETS = Set.of("ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s]{7,20}$");
    private static final Pattern CHANNEL = Pattern.compile("^[A-Z0-9_]{1,30}$");
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ProductRepository products;
    private final PackageBookingRepository bookings;
    private final PriceCalculator prices;
    private final ChannelCommissionRepository commissions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PackageBookingService(ProductRepository products, PackageBookingRepository bookings) {
        this(products, bookings, new CostBasedPriceCalculator(), Clock.systemUTC());
    }

    public PackageBookingService(ProductRepository products, PackageBookingRepository bookings, Clock clock) {
        this(products, bookings, new CostBasedPriceCalculator(), clock);
    }

    public PackageBookingService(ProductRepository products, PackageBookingRepository bookings,
                                 PriceCalculator prices, Clock clock) {
        this(products, bookings, prices, ChannelCommissionRepository.NONE, clock);
    }

    public PackageBookingService(ProductRepository products, PackageBookingRepository bookings,
                                 PriceCalculator prices, ChannelCommissionRepository commissions, Clock clock) {
        this.products = products;
        this.bookings = bookings;
        this.prices = prices;
        this.commissions = commissions;
        this.clock = clock;
    }

    // ---- create --------------------------------------------------------------------------------

    public Response create(CreateRequest req) {
        if (req == null || req.productCode() == null || req.productCode().isBlank()) {
            throw new ValidationException("productCode is required");
        }
        LocalDate departureDate = parseDate(req.departureDate(), "departureDate");
        validateContact(req.contact());
        String channel = normalizeChannel(req.channelCode());
        List<TravelerInput> inputs = req.travelers() == null ? List.of() : req.travelers();
        if (inputs.isEmpty() || inputs.size() > MAX_TRAVELERS) {
            throw new ValidationException("travelers must contain 1 to " + MAX_TRAVELERS + " people");
        }

        String code = req.productCode().trim();
        ProductData data = products.findProduct(code)
                .orElseThrow(() -> new PackageNotFoundException(code, departureDate));
        ProductData.Product p = data.product();
        LocalDate today = BusinessTime.today(clock);

        // Storage rule S1 applies only to dates that are valid departures of an active product.
        Map<LocalDate, DepartureRow> rows = products.findDepartureRows(code);
        if (!p.active() || !DepartureCalculator.isDeparture(data, rows, departureDate, today)) {
            throw new PackageNotBookableException("This date is not an open departure of the package");
        }
        DepartureRow stored = rows.get(departureDate);
        if (stored != null && stored.status() != DepartureRow.Status.OPEN) {
            throw new PackageNotBookableException("This departure is not open for booking");
        }

        List<Traveler> travelers = new ArrayList<>();
        int adults = 0;
        int infants = 0;
        int seats = 0;
        long total = 0;
        for (int i = 0; i < inputs.size(); i++) {
            TravelerInput in = inputs.get(i);
            String who = "travelers[" + i + "]";
            if (in == null || in.targetType() == null || !TARGETS.contains(in.targetType())) {
                throw new ValidationException(who + ".targetType must be one of " + TARGETS);
            }
            PriceCalculator.Quote quote = prices.quote(data, departureDate, in.targetType());
            if (quote == null || !quote.salesAvailable()) {
                throw new ValidationException(who + ": " + in.targetType() + " is not sold for this package");
            }
            String name = in.name() == null ? "" : in.name().trim();
            if (name.isEmpty() || name.length() > 100) throw new ValidationException(who + ".name is required (max 100)");
            LocalDate birth = parseDate(in.birthDate(), who + ".birthDate");
            if (birth.isAfter(today)) throw new ValidationException(who + ".birthDate cannot be in the future");
            if (!"M".equals(in.gender()) && !"F".equals(in.gender())) {
                throw new ValidationException(who + ".gender must be M or F");
            }

            long unit = quote.payable();
            // Commission policy of the booking channel (none for DIRECT unless configured), taken from the
            // sale price after discount. Cost is null for manually priced traveler types.
            long commission = commissions.resolve(channel, p.code())
                    .map(c -> PricingRules.commission(quote.afterDiscount(), c.commissionBp(), c.commissionFixed()))
                    .orElse(0L);
            travelers.add(new Traveler(i + 1, in.targetType(), name, birth, in.gender(), unit, quote.salePrice(),
                    quote.discount(), quote.passThrough(), quote.cost(), commission));
            total += unit;
            if ("ADULT".equals(in.targetType())) adults++;
            if ("INFANT".equals(in.targetType())) infants++;
            else seats++; // infants do not occupy a seat
        }
        if (adults < 1) throw new ValidationException("at least one adult is required");
        if (infants > adults) throw new ValidationException("infants cannot outnumber adults");

        Instant now = clock.instant();
        PackageBookingData booking = new PackageBookingData(
                newBookingCode(), p.code(), departureDate, Status.PENDING_PAYMENT, channel, p.name(),
                req.contact().name().trim(), req.contact().phone().trim(), blankToNull(req.contact().email()),
                seats, total, 0, 0, null, null, now, now.plus(PAYMENT_WINDOW), null, null, travelers);

        bookings.createWithSeatHold(booking, p.defaultCapacity()); // throws InsufficientSeatsException (409)
        return toResponse(booking);
    }

    // ---- read / pay / cancel -------------------------------------------------------------------

    public Response get(String bookingCode) {
        return toResponse(load(bookingCode));
    }

    /** Booking plus the internal price snapshot (cost, commission, profit). Admin use only. */
    public AdminBookingDetail getForAdmin(String bookingCode) {
        PackageBookingData b = load(bookingCode);
        List<TravelerFinance> finance = new ArrayList<>();
        long totalCost = 0;
        long totalCommission = 0;
        long totalAfterDiscount = 0;
        boolean costKnown = true;
        for (Traveler t : b.travelers()) {
            long afterDiscount = Math.max(0, t.unitSalePrice() - t.unitDiscount());
            finance.add(new TravelerFinance(t.seq(), t.unitSalePrice(), t.unitDiscount(), t.unitPassThrough(),
                    t.unitCost(), t.unitCommission()));
            totalAfterDiscount += afterDiscount;
            totalCommission += t.unitCommission() == null ? 0 : t.unitCommission();
            if (t.unitCost() == null) costKnown = false;
            else totalCost += t.unitCost();
        }
        Long profit = costKnown ? totalAfterDiscount - totalCost - totalCommission : null;
        return new AdminBookingDetail(toResponse(b), finance, costKnown ? totalCost : null, totalCommission, profit);
    }

    public Response pay(String bookingCode, String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new ValidationException("paymentReference is required");
        }
        PackageBookingData b = load(bookingCode);
        if (b.status() != Status.PENDING_PAYMENT) {
            throw new PackageNotBookableException("Cannot pay a booking in status " + b.status());
        }
        PackageBookingData updated = b.transition(Status.CONFIRMED, paymentReference.trim(), null, 0, 0,
                clock.instant(), null);
        if (!bookings.transition(updated, Status.PENDING_PAYMENT, 0)) {
            throw new PackageNotBookableException("Booking was changed by another request; reload and retry");
        }
        return toResponse(updated);
    }

    public Response cancel(String bookingCode, String reason) {
        PackageBookingData b = load(bookingCode);
        if (b.status() == Status.CANCELLED || b.status() == Status.EXPIRED) {
            throw new PackageNotBookableException("Cannot cancel a booking in status " + b.status());
        }
        long penalty = 0;
        long refund = 0;
        if (b.status() == Status.CONFIRMED) {
            long days = ChronoUnit.DAYS.between(BusinessTime.today(clock), b.departureDate());
            penalty = b.totalAmount() * penaltyPercent(days) / 100;
            refund = b.totalAmount() - penalty;
        }
        PackageBookingData updated = b.transition(Status.CANCELLED, b.paymentReference(),
                reason == null || reason.isBlank() ? "Customer request" : reason.trim(), refund, penalty,
                b.confirmedAt(), clock.instant());
        if (!bookings.transition(updated, b.status(), b.seatCount())) {
            throw new PackageNotBookableException("Booking was changed by another request; reload and retry");
        }
        return toResponse(updated);
    }

    /** Expires every unpaid booking whose payment window has passed (seats are released). */
    public int expireOverdue() {
        int expired = 0;
        for (PackageBookingData b : bookings.findPendingDueBefore(clock.instant().plusNanos(1))) {
            PackageBookingData next = b.transition(Status.EXPIRED, null, null, 0, 0, null, null);
            if (bookings.transition(next, Status.PENDING_PAYMENT, b.seatCount())) expired++;
        }
        return expired;
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** Sample cancellation policy: percentage of the paid amount kept as penalty by days before departure. */
    static int penaltyPercent(long daysBeforeDeparture) {
        if (daysBeforeDeparture >= 30) return 0;
        if (daysBeforeDeparture >= 20) return 10;
        if (daysBeforeDeparture >= 10) return 20;
        if (daysBeforeDeparture >= 3) return 30;
        return 50;
    }

    /** Loads a booking and lazily expires it when the payment window has passed. */
    private PackageBookingData load(String bookingCode) {
        if (bookingCode == null || bookingCode.isBlank()) throw new PackageBookingNotFoundException("null");
        String code = bookingCode.trim();
        PackageBookingData b = bookings.findByCode(code).orElseThrow(() -> new PackageBookingNotFoundException(code));
        if (b.status() == Status.PENDING_PAYMENT && !clock.instant().isBefore(b.paymentDueAt())) {
            PackageBookingData expired = b.transition(Status.EXPIRED, null, null, 0, 0, null, null);
            if (bookings.transition(expired, Status.PENDING_PAYMENT, b.seatCount())) {
                return expired;
            }
            return bookings.findByCode(code).orElseThrow(() -> new PackageBookingNotFoundException(code));
        }
        return b;
    }

    private void validateContact(Contact c) {
        if (c == null || c.name() == null || c.name().isBlank()) throw new ValidationException("contact.name is required");
        if (c.phone() == null || !PHONE.matcher(c.phone().trim()).matches()) {
            throw new ValidationException("contact.phone is required (digits, +, -)");
        }
        if (c.email() != null && !c.email().isBlank() && !EMAIL.matcher(c.email().trim()).matches()) {
            throw new ValidationException("contact.email is not a valid address");
        }
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) return PackageBookingData.DIRECT_CHANNEL;
        String c = channel.trim().toUpperCase();
        if (!CHANNEL.matcher(c).matches()) {
            throw new ValidationException("channelCode must be 1-30 characters of A-Z, 0-9 or _");
        }
        return c;
    }

    private static LocalDate parseDate(String s, String field) {
        try {
            return LocalDate.parse(s == null ? "" : s.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(field + " must be yyyy-MM-dd");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String newBookingCode() {
        StringBuilder sb = new StringBuilder("PK");
        for (int i = 0; i < 8; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    private static Response toResponse(PackageBookingData b) {
        List<TravelerView> travelers = b.travelers().stream()
                .map(t -> new TravelerView(t.seq(), t.targetType(), t.fullName(), t.birthDate().toString(),
                        t.gender(), t.unitPrice()))
                .toList();
        return new Response(b.bookingCode(), b.productCode(), b.departureDate().toString(), b.packageName(),
                b.status().name(), b.channelCode(), new Contact(b.contactName(), b.contactPhone(), b.contactEmail()),
                travelers, b.seatCount(), b.totalAmount(), b.refundAmount(), b.penaltyAmount(),
                b.paymentReference(), b.cancelReason(), iso(b.createdAt()), iso(b.paymentDueAt()),
                iso(b.confirmedAt()), iso(b.cancelledAt()));
    }

    private static String iso(Instant i) {
        return i == null ? null : i.toString();
    }
}
