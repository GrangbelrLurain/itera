package aerocore.backend.exception;

/** Missing/invalid admin credentials or token (HTTP 401). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
