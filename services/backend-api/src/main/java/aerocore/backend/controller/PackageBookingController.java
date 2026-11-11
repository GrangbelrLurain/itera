package aerocore.backend.controller;

import aerocore.backend.dto.AdminRequests.CancelRequest;
import aerocore.backend.dto.AdminRequests.PaymentRequest;
import aerocore.backend.dto.PackageBookingDtos;
import aerocore.backend.service.PackageBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/package-bookings")
@Tag(name = "Package bookings")
public class PackageBookingController {

    private final ObjectProvider<PackageBookingService> bookings;

    public PackageBookingController(ObjectProvider<PackageBookingService> bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createPackageBooking", summary = "Book seats of a departure (pending payment)")
    public PackageBookingDtos.Response create(@RequestBody PackageBookingDtos.CreateRequest request) {
        return bookings.getObject().create(request);
    }

    @GetMapping("/{bookingCode}")
    @Operation(operationId = "getPackageBooking", summary = "Booking by code")
    public PackageBookingDtos.Response get(@PathVariable String bookingCode) {
        return bookings.getObject().get(bookingCode);
    }

    @PostMapping("/{bookingCode}/pay")
    @Operation(operationId = "payPackageBooking", summary = "Confirm the payment of a pending booking")
    public PackageBookingDtos.Response pay(@PathVariable String bookingCode,
                                           @RequestBody(required = false) PaymentRequest body) {
        return bookings.getObject().pay(bookingCode, body == null ? null : body.paymentReference());
    }

    @PostMapping("/{bookingCode}/cancel")
    @Operation(operationId = "cancelPackageBooking", summary = "Cancel a booking (penalty by days before departure)")
    public PackageBookingDtos.Response cancel(@PathVariable String bookingCode,
                                              @RequestBody(required = false) CancelRequest body) {
        return bookings.getObject().cancel(bookingCode, body == null ? null : body.reason());
    }
}
