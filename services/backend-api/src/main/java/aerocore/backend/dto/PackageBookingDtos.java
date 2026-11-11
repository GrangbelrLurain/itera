package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Package booking request/response shapes. */
public final class PackageBookingDtos {

    private PackageBookingDtos() {}

    /** departureDate: yyyy-MM-dd. channelCode is optional (defaults to DIRECT). */
    @Schema(name = "CreatePackageBookingRequest")
    public record CreateRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date") String departureDate,
            String channelCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Contact contact,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TravelerInput> travelers) {}

    public record Contact(String name, String phone, @Nullable String email) {}

    /** gender: "M" or "F"; birthDate: yyyy-MM-dd. */
    public record TravelerInput(
            @Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType,
            String name, @Schema(format = "date") String birthDate,
            @Schema(allowableValues = {"M", "F"}) String gender) {}

    /** Timestamps are ISO-8601 UTC strings. */
    @Schema(name = "PackageBooking")
    public record Response(
            String bookingCode, String productCode, @Schema(format = "date") String departureDate,
            String packageName,
            @Schema(allowableValues = {"PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED"}) String status,
            String channelCode, Contact contact, List<TravelerView> travelers, int seatCount,
            long totalAmount, long refundAmount, long penaltyAmount,
            @Nullable String paymentReference, @Nullable String cancelReason,
            @Schema(format = "date-time") String createdAt, @Schema(format = "date-time") String paymentDueAt,
            @Nullable @Schema(format = "date-time") String confirmedAt,
            @Nullable @Schema(format = "date-time") String cancelledAt) {}

    @Schema(name = "PackageBookingTraveler")
    public record TravelerView(
            int seq, @Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType,
            String name, @Schema(format = "date") String birthDate,
            @Schema(allowableValues = {"M", "F"}) String gender, long unitPrice) {}
}
