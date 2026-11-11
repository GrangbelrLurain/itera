package aerocore.backend.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A package booking as stored (immutable snapshot; state changes create a new instance). */
public record PackageBookingData(
        String bookingCode, String productCode, LocalDate departureDate, Status status, String channelCode,
        String packageName, String contactName, String contactPhone, String contactEmail,
        int seatCount, long totalAmount, long refundAmount, long penaltyAmount,
        String paymentReference, String cancelReason,
        Instant createdAt, Instant paymentDueAt, Instant confirmedAt, Instant cancelledAt,
        List<Traveler> travelers) {

    public static final String DIRECT_CHANNEL = "DIRECT";

    public enum Status { PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED }

    /**
     * Per-traveler price snapshot (docs/design/package-pricing.md section 4).
     * unitCost and unitCommission stay null until cost-based pricing exists.
     */
    public record Traveler(int seq, String targetType, String fullName, LocalDate birthDate, String gender,
                           long unitPrice, long unitSalePrice, long unitDiscount, long unitPassThrough,
                           Long unitCost, Long unitCommission) {}

    /** Copy with a new state and the fields that change together with it. */
    public PackageBookingData transition(Status newStatus, String paymentReference, String cancelReason,
                                         long refundAmount, long penaltyAmount, Instant confirmedAt,
                                         Instant cancelledAt) {
        return new PackageBookingData(bookingCode, productCode, departureDate, newStatus, channelCode, packageName,
                contactName, contactPhone, contactEmail, seatCount, totalAmount, refundAmount, penaltyAmount,
                paymentReference, cancelReason, createdAt, paymentDueAt, confirmedAt, cancelledAt, travelers);
    }

    public PackageBookingData withTravelers(List<Traveler> newTravelers) {
        return new PackageBookingData(bookingCode, productCode, departureDate, status, channelCode, packageName,
                contactName, contactPhone, contactEmail, seatCount, totalAmount, refundAmount, penaltyAmount,
                paymentReference, cancelReason, createdAt, paymentDueAt, confirmedAt, cancelledAt, newTravelers);
    }
}
