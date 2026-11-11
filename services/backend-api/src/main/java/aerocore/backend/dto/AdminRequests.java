package aerocore.backend.dto;

import aerocore.backend.dto.AdminPricingDtos.CommissionInput;
import aerocore.backend.dto.AdminPricingDtos.CostInput;
import aerocore.backend.dto.AdminProductDtos.ItineraryInput;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Small request bodies shared by the admin and booking APIs. Required members are validated (400 otherwise). */
public final class AdminRequests {

    private AdminRequests() {}

    public record ActiveRequest(@NotNull Boolean active) {}

    public record CostRowsRequest(@NotNull List<CostInput> rows) {}

    public record CommissionRowsRequest(@NotNull List<CommissionInput> rows) {}

    public record ItineraryRowsRequest(@NotNull List<ItineraryInput> rows) {}

    public record PaymentRequest(String paymentReference) {}

    public record CancelRequest(String reason) {}
}
