package aerocore.backend.exception;

import java.time.LocalDate;

/** Unknown product, or the date is not a departure of the product. */
public class PackageNotFoundException extends RuntimeException {
    public PackageNotFoundException(String productCode, LocalDate departureDate) {
        super("Package departure not found: " + productCode + (departureDate == null ? "" : " on " + departureDate));
    }
}
