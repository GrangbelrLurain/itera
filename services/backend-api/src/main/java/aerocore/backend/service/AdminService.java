package aerocore.backend.service;

import aerocore.backend.dto.AdminDtos.BookingListResponse;
import aerocore.backend.dto.AdminDtos.BookingSummary;
import aerocore.backend.dto.AdminDtos.Dashboard;
import aerocore.backend.dto.AdminDtos.LowSeatDeparture;
import aerocore.backend.model.BookingPage;
import aerocore.backend.model.BookingSearch;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.model.PackageBookingData.Status;
import aerocore.backend.repository.PackageBookingRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read models for the admin screens (booking list, dashboard). Mutations go through PackageBookingService. */
public class AdminService {

    static final int LOW_SEAT_THRESHOLD = 5;
    static final int MAX_PAGE_SIZE = 100;

    private final PackageBookingService bookingService;
    private final PackageBookingRepository bookings;
    private final PackageService packages;
    private final Clock clock;

    public AdminService(PackageBookingService bookingService, PackageBookingRepository bookings,
                        PackageService packages, Clock clock) {
        this.bookingService = bookingService;
        this.bookings = bookings;
        this.packages = packages;
        this.clock = clock;
    }

    public BookingListResponse searchBookings(BookingSearch search, int page, int size) {
        bookingService.expireOverdue(); // keep the list consistent without a background job
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        BookingPage result = bookings.search(search == null ? BookingSearch.none() : search, safePage, safeSize);
        List<BookingSummary> items = result.items().stream().map(AdminService::toSummary).toList();
        return new BookingListResponse(items, result.total(), safePage, safeSize);
    }

    public Dashboard dashboard() {
        bookingService.expireOverdue();
        Map<Status, Long> counts = bookings.countByStatus();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (Status s : Status.values()) statusCounts.put(s.name(), counts.getOrDefault(s, 0L));

        Instant now = clock.instant();
        long expiringSoon = bookings.countPendingDueBetween(now, now.plus(Duration.ofHours(24)));

        List<LowSeatDeparture> low = packages.lowSeatDepartures(LOW_SEAT_THRESHOLD).stream()
                .map(l -> new LowSeatDeparture(l.productCode(), l.name(), l.departureDate(), l.remainingSeats()))
                .toList();

        return new Dashboard(statusCounts, expiringSoon, bookings.confirmedRevenue(), low);
    }

    private static BookingSummary toSummary(PackageBookingData b) {
        return new BookingSummary(b.bookingCode(), b.productCode(), b.packageName(), b.departureDate().toString(),
                b.status().name(), b.channelCode(), b.contactName(), b.contactPhone(), b.seatCount(), b.totalAmount(),
                b.createdAt().toString(), b.paymentDueAt().toString());
    }
}
