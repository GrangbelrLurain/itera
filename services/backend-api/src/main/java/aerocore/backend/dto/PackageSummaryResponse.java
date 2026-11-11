package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Row of the package list: one departure of a product. */
@Schema(name = "PackageSummary")
public record PackageSummaryResponse(
        String productCode, @Schema(format = "date") String departureDate, String name, int nights, int days,
        @Schema(allowableValues = {"OPEN", "FULL", "CLOSED"}) String status, int remainingSeats,
        long adultTotalPrice, long adultDiscountedTotalPrice) {

    @Schema(name = "PackageSummaryPage")
    public record Page(List<PackageSummaryResponse> items, long total, int page, int size) {}
}
