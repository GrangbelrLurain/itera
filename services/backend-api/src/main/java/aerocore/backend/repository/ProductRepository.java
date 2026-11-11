package aerocore.backend.repository;

import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.ProductData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductRepository {

    /** Full product with all child rows; empty if the code is unknown. */
    Optional<ProductData> findProduct(String productCode);

    /** Active products with prices, rules and exclusions only (no itinerary/remark/etc.) for list screens. */
    List<ProductData> findActiveProductSummaries();

    /** Same as {@link #findActiveProductSummaries()} but includes inactive products (admin screens). */
    List<ProductData> findAllProductSummaries();

    /** Stored departure rows of one product, keyed by date. */
    Map<LocalDate, DepartureRow> findDepartureRows(String productCode);

    /** Stored departure rows of all products: productCode -> date -> row. */
    Map<String, Map<LocalDate, DepartureRow>> findAllDepartureRows();
}
