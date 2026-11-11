package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/** Admin API shapes. */
public final class AdminDtos {

    private AdminDtos() {}

    public record LoginRequest(@NotNull String username, @NotNull String password) {}

    /** expiresAt: ISO-8601 UTC. */
    public record LoginResponse(String token, @Schema(format = "date-time") String expiresAt) {}

    public record BookingSummary(
            String bookingCode, String productCode, String packageName, @Schema(format = "date") String departureDate,
            @Schema(allowableValues = {"PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED"}) String status,
            String channelCode, String contactName, String contactPhone, int seatCount, long totalAmount,
            @Schema(format = "date-time") String createdAt, @Schema(format = "date-time") String paymentDueAt) {}

    @Schema(name = "AdminBookingPage")
    public record BookingListResponse(List<BookingSummary> items, long total, int page, int size) {}

    /** Internal price snapshot of one traveler (not part of the public booking response). */
    public record TravelerFinance(int seq, long unitSalePrice, long unitDiscount, long unitPassThrough,
                                  Long unitCost, Long unitCommission) {}

    /**
     * Admin view of a booking. totalCost and profit are null when any traveler was manually priced (no cost data).
     * profit = sale price after discount - cost - commission, summed over travelers.
     */
    public record AdminBookingDetail(PackageBookingDtos.Response booking, List<TravelerFinance> finance,
                                     Long totalCost, long totalCommission, Long profit) {}

    public record LowSeatDeparture(String productCode, String name, @Schema(format = "date") String departureDate,
                                   int remainingSeats) {}

    /** statusCounts has an entry for every status (0 when none). */
    public record Dashboard(Map<String, Long> statusCounts, long pendingExpiringWithin24h, long confirmedRevenue,
                            List<LowSeatDeparture> lowSeatDepartures) {}
}
