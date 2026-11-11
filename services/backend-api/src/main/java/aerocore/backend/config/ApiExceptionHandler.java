package aerocore.backend.config;

import aerocore.backend.dto.ErrorResponse;
import aerocore.backend.exception.BookingNotFoundException;
import aerocore.backend.exception.InsufficientSeatsException;
import aerocore.backend.exception.InvalidTransitionException;
import aerocore.backend.exception.OperationNotAllowedException;
import aerocore.backend.exception.PackageBookingNotFoundException;
import aerocore.backend.exception.PackageNotBookableException;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ProductExistsException;
import aerocore.backend.exception.SeatConflictException;
import aerocore.backend.exception.UnauthorizedException;
import aerocore.backend.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** One JSON error body ({error, code}) and status mapping for the whole API. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, code));
    }

    @ExceptionHandler({ValidationException.class, HttpMessageNotReadableException.class, TypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> invalid(Exception e) {
        String message = e instanceof ValidationException ? e.getMessage() : "Malformed request: " + e.getMessage();
        if (e instanceof HttpMessageNotReadableException) message = "Request body is missing or not valid JSON";
        return body(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).findFirst().orElse("Request body is not valid");
        return body(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(new ErrorResponse(e.getMessage(), "UNAUTHENTICATED"));
    }

    @ExceptionHandler({PackageNotFoundException.class, PackageBookingNotFoundException.class,
            BookingNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> notFound(Exception e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", e instanceof NoResourceFoundException
                ? "Endpoint not found: /" + ((NoResourceFoundException) e).getResourcePath() : e.getMessage());
    }

    @ExceptionHandler({InsufficientSeatsException.class, SeatConflictException.class})
    ResponseEntity<ErrorResponse> seatConflict(Exception e) {
        return body(HttpStatus.CONFLICT, "SEAT_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(ProductExistsException.class)
    ResponseEntity<ErrorResponse> exists(ProductExistsException e) {
        return body(HttpStatus.CONFLICT, "ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler({PackageNotBookableException.class, OperationNotAllowedException.class,
            InvalidTransitionException.class})
    ResponseEntity<ErrorResponse> precondition(Exception e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "FAILED_PRECONDITION", e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.getMessage());
    }

    /** A service whose configuration is missing (database or admin password). */
    @ExceptionHandler(NoSuchBeanDefinitionException.class)
    ResponseEntity<ErrorResponse> unavailable(NoSuchBeanDefinitionException e) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE",
                "This API is not configured (set DB_URL, and ADMIN_PASSWORD for the admin API)");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        LOG.error("Unhandled server error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }
}
