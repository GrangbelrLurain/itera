package aerocore.backend.controller;

import aerocore.backend.dto.AdminProductDtos.CapacityRequest;
import aerocore.backend.dto.AdminProductDtos.DepartureDateRequest;
import aerocore.backend.dto.AdminProductDtos.DepartureItem;
import aerocore.backend.dto.AdminProductDtos.ExtrasInput;
import aerocore.backend.dto.AdminProductDtos.ProductDetail;
import aerocore.backend.dto.AdminProductDtos.ProductInput;
import aerocore.backend.dto.AdminProductDtos.ProductSummary;
import aerocore.backend.dto.AdminProductDtos.ScheduleInput;
import aerocore.backend.dto.AdminRequests.ActiveRequest;
import aerocore.backend.dto.AdminRequests.ItineraryRowsRequest;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.service.AdminProductService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Master products (모상품), their schedule rules, extras and the departure dates computed from them. */
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin products")
public class AdminProductController {

    private static final String DATE_MESSAGE = "date must be yyyy-MM-dd";

    private final ObjectProvider<AdminProductService> products;

    public AdminProductController(ObjectProvider<AdminProductService> products) {
        this.products = products;
    }

    private AdminProductService service() {
        return products.getObject();
    }

    private static String code(String productCode) {
        return productCode.toUpperCase();
    }

    @GetMapping
    @Operation(operationId = "listAdminProducts", summary = "All master products")
    public List<ProductSummary> list() {
        return service().listProducts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createAdminProduct", summary = "Create a master product")
    public ProductDetail create(@RequestBody ProductInput input) {
        return service().createProduct(input);
    }

    @GetMapping("/{productCode}")
    @Operation(operationId = "getAdminProduct", summary = "Product with schedule, itinerary and extras")
    public ProductDetail get(@PathVariable String productCode) {
        return service().getProduct(code(productCode));
    }

    @PutMapping("/{productCode}")
    @Operation(operationId = "updateAdminProduct", summary = "Update the basic information and prices")
    public ProductDetail update(@PathVariable String productCode, @RequestBody ProductInput input) {
        return service().updateProduct(code(productCode), input);
    }

    @PostMapping("/{productCode}/active")
    @Operation(operationId = "setAdminProductActive", summary = "Enable or disable sales of a product")
    public ProductDetail setActive(@PathVariable String productCode, @Valid @RequestBody ActiveRequest body) {
        if (body == null || body.active() == null) throw new ValidationException("active (true/false) is required");
        return service().setActive(code(productCode), body.active());
    }

    @PutMapping("/{productCode}/itinerary")
    @Operation(operationId = "replaceAdminItinerary", summary = "Replace the itinerary")
    public ProductDetail replaceItinerary(@PathVariable String productCode, @Valid @RequestBody ItineraryRowsRequest body) {
        return service().replaceItinerary(code(productCode), body.rows());
    }

    @PutMapping("/{productCode}/schedule")
    @Operation(operationId = "replaceAdminSchedule", summary = "Replace the departure rules and excluded dates")
    public ProductDetail replaceSchedule(@PathVariable String productCode, @RequestBody ScheduleInput body) {
        return service().replaceSchedule(code(productCode), body);
    }

    @PutMapping("/{productCode}/extras")
    @Operation(operationId = "replaceAdminExtras", summary = "Replace optional tours, shopping, remarks and conditions")
    public ProductDetail replaceExtras(@PathVariable String productCode, @RequestBody ExtrasInput body) {
        return service().replaceExtras(code(productCode), body);
    }

    @GetMapping("/{productCode}/departures")
    @Operation(operationId = "listAdminDepartures", summary = "Departure dates of a range (rule dates plus stored rows)")
    public List<DepartureItem> departures(
            @PathVariable String productCode,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String from,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String to) {
        return service().departures(code(productCode), Params.date(from), Params.date(to));
    }

    @PostMapping("/{productCode}/departures")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addAdminDeparture", summary = "Add a single departure date outside the rules")
    public DepartureItem addDeparture(@PathVariable String productCode, @Valid @RequestBody DepartureDateRequest body) {
        return service().addDeparture(code(productCode), Params.requiredDate(body.date(), DATE_MESSAGE));
    }

    @PostMapping("/{productCode}/departures/{date}/close")
    @Operation(operationId = "closeAdminDeparture", summary = "Stop new bookings of a departure")
    public DepartureItem close(@PathVariable String productCode,
                               @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        return service().closeDeparture(code(productCode), Params.requiredDate(date, DATE_MESSAGE));
    }

    @PostMapping("/{productCode}/departures/{date}/reopen")
    @Operation(operationId = "reopenAdminDeparture", summary = "Reopen a closed or canceled departure")
    public DepartureItem reopen(@PathVariable String productCode,
                                @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        return service().reopenDeparture(code(productCode), Params.requiredDate(date, DATE_MESSAGE));
    }

    @PostMapping("/{productCode}/departures/{date}/cancel")
    @Operation(operationId = "cancelAdminDeparture", summary = "Cancel a departure (only without bookings)")
    public DepartureItem cancel(@PathVariable String productCode,
                                @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        return service().cancelDeparture(code(productCode), Params.requiredDate(date, DATE_MESSAGE));
    }

    @PutMapping("/{productCode}/departures/{date}/capacity")
    @Operation(operationId = "setAdminDepartureCapacity", summary = "Change the seat capacity of a departure")
    public DepartureItem setCapacity(@PathVariable String productCode,
                                     @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date,
                                     @Valid @RequestBody CapacityRequest body) {
        LocalDate d = Params.requiredDate(date, DATE_MESSAGE);
        return service().setCapacity(code(productCode), d, body.capacity());
    }
}
