package aerocore.backend.controller;

import aerocore.backend.dto.AdminPricingDtos.CommissionInput;
import aerocore.backend.dto.AdminPricingDtos.CostOverrideView;
import aerocore.backend.dto.AdminPricingDtos.PricingInput;
import aerocore.backend.dto.AdminPricingDtos.PricingPreview;
import aerocore.backend.dto.AdminPricingDtos.PricingView;
import aerocore.backend.dto.AdminRequests.CommissionRowsRequest;
import aerocore.backend.dto.AdminRequests.CostRowsRequest;
import aerocore.backend.service.AdminPricingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Cost components, mark-up, per-date cost overrides and channel commissions. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin pricing")
public class AdminPricingController {

    private final ObjectProvider<AdminPricingService> pricing;

    public AdminPricingController(ObjectProvider<AdminPricingService> pricing) {
        this.pricing = pricing;
    }

    private AdminPricingService service() {
        return pricing.getObject();
    }

    @GetMapping("/channel-commissions")
    @Operation(operationId = "listAdminChannelCommissions", summary = "Commission rules of the sales channels")
    public List<CommissionInput> listCommissions() {
        return service().listCommissions();
    }

    @PutMapping("/channel-commissions")
    @Operation(operationId = "replaceAdminChannelCommissions", summary = "Replace the whole commission table")
    public List<CommissionInput> replaceCommissions(@Valid @RequestBody CommissionRowsRequest body) {
        return service().replaceCommissions(body.rows());
    }

    @GetMapping("/products/{productCode}/pricing")
    @Operation(operationId = "getAdminPricing", summary = "Cost components and mark-up of a product")
    public PricingView getPricing(@PathVariable String productCode) {
        return service().getPricing(productCode.toUpperCase());
    }

    @PutMapping("/products/{productCode}/pricing")
    @Operation(operationId = "saveAdminPricing", summary = "Replace the cost components and mark-up")
    public PricingView savePricing(@PathVariable String productCode, @RequestBody PricingInput body) {
        return service().savePricing(productCode.toUpperCase(), body);
    }

    @GetMapping("/products/{productCode}/pricing/preview")
    @Operation(operationId = "previewAdminPricing",
            summary = "Sale price, commission and profit per traveler type for a channel (and optionally a date)")
    public PricingPreview preview(
            @PathVariable String productCode,
            @Parameter(description = "Sales channel code (default DIRECT)") @RequestParam(required = false) String channel,
            @Parameter(description = "Departure date whose cost overrides are applied (default: product costs only)",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) String date) {
        return service().preview(productCode.toUpperCase(), channel, Params.date(date));
    }

    @GetMapping("/products/{productCode}/departures/{date}/cost-overrides")
    @Operation(operationId = "getAdminCostOverrides", summary = "The product's cost rows and the cost overrides of one departure date")
    public CostOverrideView getCostOverrides(
            @PathVariable String productCode,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        return service().getCostOverrides(productCode.toUpperCase(),
                Params.requiredDate(date, "date must be yyyy-MM-dd"));
    }

    @PutMapping("/products/{productCode}/departures/{date}/cost-overrides")
    @Operation(operationId = "saveAdminCostOverrides",
            summary = "Replace the cost overrides of a date; an empty list reverts the date to the product costs")
    public CostOverrideView saveCostOverrides(
            @PathVariable String productCode,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date,
            @Valid @RequestBody CostRowsRequest body) {
        return service().saveCostOverrides(productCode.toUpperCase(),
                Params.requiredDate(date, "date must be yyyy-MM-dd"), body.rows());
    }
}
