package aerocore.backend.repository;

import aerocore.backend.model.ProductData;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Write side of the product model. Reads stay in ProductRepository.
 * Departure methods implement storage rules S2/S3 of docs/design/package-departure.md: they create the
 * departure row if needed (capacity = {@code capacityIfNew}) and mark it pinned.
 */
public interface ProductAdminRepository {

    boolean exists(String productCode);

    /** Inserts the product and its base prices (rules, itinerary and extras start empty). */
    void insertProduct(ProductData.Product product, List<ProductData.Price> prices);

    /** Updates the product row and replaces its base prices. */
    void updateProduct(ProductData.Product product, List<ProductData.Price> prices);

    void setActive(String productCode, boolean active);

    void replaceItinerary(String productCode, List<ProductData.ItineraryRow> rows);

    /** Replaces rules and exclusions only; stored departure rows are never touched. */
    void replaceSchedule(String productCode, List<ProductData.Rule> rules, Set<LocalDate> exclusions);

    /** Replaces optional tours, shopping stops, remark and conditions (null remark/condition clears them). */
    void replaceExtras(String productCode, List<ProductData.OptionalTourRow> tours,
                       List<ProductData.ShoppingRow> shoppings, ProductData.Remark remark,
                       ProductData.Condition condition);

    /** Replaces the cost rows and the margin policy (docs/design/package-pricing.md). */
    void replacePricing(String productCode, List<ProductData.CostRow> costs, int marginBp, long marginFixed);

    /**
     * Replaces the cost overrides of one departure. Creates the departure row if needed (capacity =
     * {@code capacityIfNew}) and pins it, because the overrides hang on it (storage rule S2).
     */
    void replaceCostOverrides(String productCode, LocalDate date, List<ProductData.CostRow> rows, int capacityIfNew);

    // ---- departures ------------------------------------------------------------------------

    void closeDeparture(String productCode, LocalDate date, int capacityIfNew);

    /** CLOSED or CANCELED -> OPEN. No-op when there is no stored row. */
    void reopenDeparture(String productCode, LocalDate date);

    /** @return false (nothing changed) when {@code capacity} is below the seats already reserved */
    boolean setCapacity(String productCode, LocalDate date, int capacity, int capacityIfNew);

    /** @return false (nothing changed) when the departure has reserved seats */
    boolean cancelDeparture(String productCode, LocalDate date, int capacityIfNew);

    /** Stores the date as an extra pinned departure (idempotent). */
    void addDeparture(String productCode, LocalDate date, int capacityIfNew);
}
