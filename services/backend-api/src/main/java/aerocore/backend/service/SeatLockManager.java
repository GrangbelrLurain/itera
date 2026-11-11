package aerocore.backend.service;

import aerocore.backend.exception.SeatConflictException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe distributed seat lock manager using ConcurrentHashMap.
 * Manages aircraft seat allocations and prevents double-booking under high concurrency.
 */
public class SeatLockManager {
    // Lock key: "flightNumber:departureDate:seatNumber" -> bookingId
    private final ConcurrentHashMap<String, String> seatLocks = new ConcurrentHashMap<>();
    // Reverse map: bookingId -> lock key
    private final ConcurrentHashMap<String, String> bookingToLock = new ConcurrentHashMap<>();

    private static final int MAX_ROWS = 30;
    private static final char[] SEAT_LETTERS = {'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Atomically allocates and locks the next available seat on the specified flight and date.
     */
    public String acquireSeatLock(String flightNumber, String departureDate, String bookingId) {
        if (flightNumber == null || flightNumber.isBlank()) {
            throw new IllegalArgumentException("Flight number cannot be blank");
        }
        if (departureDate == null || departureDate.isBlank()) {
            throw new IllegalArgumentException("Departure date cannot be blank");
        }
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("Booking ID cannot be blank");
        }

        for (int row = 1; row <= MAX_ROWS; row++) {
            for (char letter : SEAT_LETTERS) {
                String seatNumber = row + String.valueOf(letter);
                String lockKey = buildLockKey(flightNumber, departureDate, seatNumber);

                if (seatLocks.putIfAbsent(lockKey, bookingId) == null) {
                    bookingToLock.put(bookingId, lockKey);
                    return seatNumber;
                }
            }
        }

        throw new SeatConflictException(
                "No seats available for flight " + flightNumber + " on " + departureDate);
    }

    /**
     * Atomically acquires lock for a specific seat.
     */
    public boolean acquireSpecificSeat(String flightNumber, String departureDate, String seatNumber, String bookingId) {
        String lockKey = buildLockKey(flightNumber, departureDate, seatNumber);
        String existing = seatLocks.putIfAbsent(lockKey, bookingId);
        if (existing == null) {
            bookingToLock.put(bookingId, lockKey);
            return true;
        }
        return false;
    }

    /**
     * Releases seat lock associated with the given bookingId.
     */
    public boolean releaseSeatLock(String bookingId) {
        String lockKey = bookingToLock.remove(bookingId);
        if (lockKey != null) {
            seatLocks.remove(lockKey);
            return true;
        }
        return false;
    }

    /**
     * Checks if a specific seat is currently locked.
     */
    public boolean isSeatLocked(String flightNumber, String departureDate, String seatNumber) {
        String lockKey = buildLockKey(flightNumber, departureDate, seatNumber);
        return seatLocks.containsKey(lockKey);
    }

    /**
     * Retrieves seat number for a booking if locked.
     */
    public String getSeatForBooking(String bookingId) {
        String lockKey = bookingToLock.get(bookingId);
        if (lockKey == null) {
            return null;
        }
        String[] parts = lockKey.split(":");
        return parts.length >= 3 ? parts[2] : null;
    }

    public int getActiveLockCount() {
        return seatLocks.size();
    }

    public void clearAll() {
        seatLocks.clear();
        bookingToLock.clear();
    }

    private String buildLockKey(String flightNumber, String departureDate, String seatNumber) {
        return flightNumber.trim().toUpperCase() + ":" + departureDate.trim() + ":" + seatNumber.trim().toUpperCase();
    }
}
