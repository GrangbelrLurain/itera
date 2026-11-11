package aerocore.backend.dto;

/**
 * Error body of every non-2xx response.
 * code: INVALID_ARGUMENT, UNAUTHENTICATED, NOT_FOUND, ALREADY_EXISTS, SEAT_CONFLICT,
 * FAILED_PRECONDITION, UNAVAILABLE, INTERNAL_ERROR.
 */
public record ErrorResponse(String error, String code) {}
