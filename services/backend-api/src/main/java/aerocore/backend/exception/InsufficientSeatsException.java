package aerocore.backend.exception;

/** Not enough remaining seats for the requested party size, or the departure is not open (HTTP 409). */
public class InsufficientSeatsException extends RuntimeException {
    public InsufficientSeatsException(String productCode, int requested) {
        super("Not enough seats left on " + productCode + " for " + requested + " traveler(s), or the departure is closed");
    }
}
