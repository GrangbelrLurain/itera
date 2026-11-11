package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cost-based pricing management (docs/design/package-pricing.md). Rates are bp = 1/100 %.
 */
public final class AdminPricingDtos {

    private AdminPricingDtos() {}

    /** One cost line of a traveler type. */
    public record CostInput(
            @Schema(allowableValues = {"FLIGHT", "HOTEL", "LAND", "TOUR", "INSURANCE", "OTHER"}) String componentType,
            @Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType,
            Long cost, @Nullable String note) {}

    /** Cost rows plus the mark-up policy of a product (null margin values mean 0). */
    public record PricingInput(List<CostInput> costs, Integer marginBp, Long marginFixed) {}

    public record PricingView(List<CostInput> costs, int marginBp, long marginFixed) {}

    /**
     * One traveler type of the price preview. mode: COST (computed from cost rows) or MANUAL (stored base price).
     * cost, margin and profit are null when they cannot be known (manual price / no policy).
     */
    public record PreviewRow(
            @Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType,
            @Schema(allowableValues = {"COST", "MANUAL"}) String mode,
            Long cost, Long margin, long salePrice, long discount,
            long afterDiscount, long passThrough, long payable, long commission, Long profit,
            boolean salesAvailable, boolean costOverridden) {}

    /** The commission policy that was applied (null values when the channel has none). */
    public record PricingPreview(String channelCode, @Nullable @Schema(format = "date") String departureDate,
                                 Integer commissionBp, Long commissionFixed, List<PreviewRow> rows) {}

    /**
     * Cost adjustments of one departure. base = the product's cost rows (read only here); overrides replace the
     * base cost with the same (component, target) or add a new cost line for that date only.
     */
    public record CostOverrideView(@Schema(format = "date") String departureDate, List<CostInput> base,
                                   List<CostInput> overrides) {}

    /** productCode null or blank = every product. */
    public record CommissionInput(String channelCode, @Nullable String productCode, Integer commissionBp, Long commissionFixed,
                                  Boolean active) {}
}
