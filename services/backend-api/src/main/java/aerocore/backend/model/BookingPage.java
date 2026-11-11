package aerocore.backend.model;

import java.util.List;

/** One page of bookings (items carry no traveler rows) and the total match count. */
public record BookingPage(List<PackageBookingData> items, long total) {}
