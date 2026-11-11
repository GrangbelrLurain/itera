package aerocore.backend;

import aerocore.backend.exception.SeatConflictException;
import aerocore.backend.service.SeatLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SeatLockManagerTest {

    private SeatLockManager seatLockManager;

    @BeforeEach
    public void setUp() {
        seatLockManager = new SeatLockManager();
    }

    public static void main(String[] args) {
        SeatLockManagerTest test = new SeatLockManagerTest();
        test.setUp();
        test.testSequentialSeatAllocation();
        test.setUp();
        test.testHighConcurrencySeatLocking();
        test.setUp();
        test.testSpecificSeatAcquisitionAndConflict();
        test.setUp();
        test.testSeatLockRelease();
        System.out.println("✔ SeatLockManagerTest all assertions passed!");
    }

    @Test
    @DisplayName("Test sequential seat allocation: 1A, 1B, 1C...")
    public void testSequentialSeatAllocation() {
        String seat1 = seatLockManager.acquireSeatLock("KE701", "2026-11-20", "BK-001");
        String seat2 = seatLockManager.acquireSeatLock("KE701", "2026-11-20", "BK-002");
        String seat3 = seatLockManager.acquireSeatLock("KE701", "2026-11-20", "BK-003");

        assertEquals("1A", seat1);
        assertEquals("1B", seat2);
        assertEquals("1C", seat3);
        assertEquals(3, seatLockManager.getActiveLockCount());
    }

    @Test
    @DisplayName("Test high concurrency seat allocation without race conditions")
    public void testHighConcurrencySeatLocking() {
        int threads = 60;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        Set<String> allocatedSeats = Collections.newSetFromMap(new ConcurrentHashMap<>());

        for (int i = 0; i < threads; i++) {
            final String bookingId = "BK-CONCUR-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String seat = seatLockManager.acquireSeatLock("OZ101", "2026-11-25", bookingId);
                    allocatedSeats.add(seat);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        try {
            boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
            assertTrue(completed, "Concurrency test timed out");
        } catch (InterruptedException e) {
            fail("Interrupted: " + e.getMessage());
        } finally {
            executor.shutdown();
        }

        assertEquals(threads, allocatedSeats.size(), "Each concurrent thread must receive a unique seat");
        assertEquals(threads, seatLockManager.getActiveLockCount());
    }

    @Test
    @DisplayName("Test specific seat reservation and conflict detection")
    public void testSpecificSeatAcquisitionAndConflict() {
        boolean first = seatLockManager.acquireSpecificSeat("KE701", "2026-11-20", "12A", "BK-SPEC-1");
        assertTrue(first, "First reservation for 12A must succeed");

        boolean second = seatLockManager.acquireSpecificSeat("KE701", "2026-11-20", "12A", "BK-SPEC-2");
        assertFalse(second, "Second reservation for 12A must be rejected due to conflict");

        assertTrue(seatLockManager.isSeatLocked("KE701", "2026-11-20", "12A"));
    }

    @Test
    @DisplayName("Test seat lock release makes seat available again")
    public void testSeatLockRelease() {
        String seat = seatLockManager.acquireSeatLock("KE701", "2026-11-20", "BK-REL-1");
        assertEquals("1A", seat);
        assertTrue(seatLockManager.isSeatLocked("KE701", "2026-11-20", "1A"));

        boolean released = seatLockManager.releaseSeatLock("BK-REL-1");
        assertTrue(released, "Seat lock must be successfully released");
        assertFalse(seatLockManager.isSeatLocked("KE701", "2026-11-20", "1A"));

        // Next booking should now be able to re-acquire 1A
        String reacquired = seatLockManager.acquireSeatLock("KE701", "2026-11-20", "BK-REL-2");
        assertEquals("1A", reacquired);
    }
}
