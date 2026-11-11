package aerocore.backend.exception;

/** The admin operation is not allowed in the current state, e.g. canceling a departure that has bookings (HTTP 422). */
public class OperationNotAllowedException extends RuntimeException {
    public OperationNotAllowedException(String message) {
        super(message);
    }
}
