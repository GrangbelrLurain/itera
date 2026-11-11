package aerocore.backend.exception;

/** A product with this code already exists (HTTP 409). */
public class ProductExistsException extends RuntimeException {
    public ProductExistsException(String code) {
        super("A product with code " + code + " already exists");
    }
}
