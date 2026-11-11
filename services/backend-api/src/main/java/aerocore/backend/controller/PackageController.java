package aerocore.backend.controller;

import aerocore.backend.dto.PackageDetailResponse;
import aerocore.backend.dto.PackageSummaryResponse;
import aerocore.backend.service.PackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/packages")
@Tag(name = "Packages")
public class PackageController {

    private final ObjectProvider<PackageService> packages;

    public PackageController(ObjectProvider<PackageService> packages) {
        this.packages = packages;
    }

    @GetMapping
    @Operation(operationId = "listPackages", summary = "Departures of the packages in a date range")
    public PackageSummaryResponse.Page listPackages(
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String from,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "open, full, closed (comma separated)") @RequestParam(required = false) String status,
            @Parameter(description = "MON,TUE,... (comma separated)") @RequestParam(required = false) String weekdays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return packages.getObject().listPackages(Params.packageSearch(from, to, keyword, status, weekdays), page, size);
    }

    @GetMapping("/{productCode}/departures/{departureDate}")
    @Operation(operationId = "getPackage", summary = "Package detail of one departure date")
    public PackageDetailResponse getPackage(
            @PathVariable String productCode,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String departureDate) {
        return packages.getObject().getDetail(productCode,
                Params.requiredDate(departureDate, "departure date must be yyyy-MM-dd"));
    }
}
