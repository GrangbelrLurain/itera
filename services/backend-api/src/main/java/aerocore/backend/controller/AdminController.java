package aerocore.backend.controller;

import aerocore.backend.dto.AdminDtos;
import aerocore.backend.dto.AdminRequests.CancelRequest;
import aerocore.backend.dto.AdminRequests.PaymentRequest;
import aerocore.backend.dto.PackageBookingDtos;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.service.AdminAuthService;
import aerocore.backend.service.AdminService;
import aerocore.backend.service.PackageBookingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

/** Admin login, dashboard and booking management. Everything except login needs a Bearer token (AdminAuthInterceptor). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class AdminController {

    private final ObjectProvider<AdminAuthService> auth;
    private final ObjectProvider<AdminService> admin;
    private final ObjectProvider<PackageBookingService> bookings;

    public AdminController(ObjectProvider<AdminAuthService> auth, ObjectProvider<AdminService> admin,
                           ObjectProvider<PackageBookingService> bookings) {
        this.auth = auth;
        this.admin = admin;
        this.bookings = bookings;
    }

    @PostMapping("/login")
    @Operation(operationId = "adminLogin", summary = "Sign in and get a bearer token", security = {})
    public AdminDtos.LoginResponse login(@Valid @RequestBody AdminDtos.LoginRequest request) {
        AdminAuthService.Login login = auth.getObject().login(request.username(), request.password());
        return new AdminDtos.LoginResponse(login.token(), login.expiresAt().toString());
    }

    @GetMapping("/dashboard")
    @Operation(operationId = "getAdminDashboard", summary = "Booking and seat summary")
    public AdminDtos.Dashboard dashboard() {
        return admin.getObject().dashboard();
    }

    @GetMapping("/bookings")
    @Operation(operationId = "listAdminBookings", summary = "Search bookings")
    public AdminDtos.BookingListResponse listBookings(
            @Parameter(description = "PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED (comma separated)")
            @RequestParam(required = false) String status,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String from,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String to,
            @RequestParam(required = false) String productCode,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String departureDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Set<PackageBookingData.Status> statuses = new HashSet<>();
        for (String s : Params.csv(status)) {
            try {
                if (!s.isBlank()) statuses.add(PackageBookingData.Status.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // unknown status values are ignored
            }
        }
        BookingSearch search = new BookingSearch(statuses, Params.date(from), Params.date(to), productCode,
                Params.date(departureDate), keyword);
        return admin.getObject().searchBookings(search, page, size);
    }

    @GetMapping("/bookings/{bookingCode}")
    @Operation(operationId = "getAdminBooking", summary = "Booking with cost, commission and profit per traveler")
    public AdminDtos.AdminBookingDetail getBooking(@PathVariable String bookingCode) {
        return bookings.getObject().getForAdmin(bookingCode);
    }

    @PostMapping("/bookings/{bookingCode}/pay")
    @Operation(operationId = "adminConfirmPayment", summary = "Confirm the payment of a pending booking")
    public PackageBookingDtos.Response pay(@PathVariable String bookingCode,
                                           @RequestBody(required = false) PaymentRequest body) {
        return bookings.getObject().pay(bookingCode, body == null ? null : body.paymentReference());
    }

    @PostMapping("/bookings/{bookingCode}/cancel")
    @Operation(operationId = "adminCancelBooking", summary = "Cancel a booking (penalty by days before departure)")
    public PackageBookingDtos.Response cancel(@PathVariable String bookingCode,
                                              @RequestBody(required = false) CancelRequest body) {
        return bookings.getObject().cancel(bookingCode, body == null ? null : body.reason());
    }
}
