package aerocore.backend.exception;

public class PackageBookingNotFoundException extends RuntimeException {
    public PackageBookingNotFoundException(String bookingCode) {
        super("Package booking not found: " + bookingCode);
    }
}
