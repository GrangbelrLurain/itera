package aerocore.backend.exception;

/** The package cannot be booked, or the booking cannot make the requested state change (HTTP 422). */
public class PackageNotBookableException extends RuntimeException {
    public PackageNotBookableException(String message) {
        super(message);
    }
}
