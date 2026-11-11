package aerocore.backend.service;

import aerocore.backend.model.ProductData;

import java.time.LocalDate;

/**
 * Price of one traveler type for a product on a date (docs/design/package-pricing.md).
 * Callers depend on this interface only; how the sale price is derived is the calculator's business.
 */
public interface PriceCalculator {

    /** @return the quote, or null when the product has no price for this traveler type. */
    Quote quote(ProductData product, LocalDate departureDate, String targetType);

    /**
     * @param salePrice       selling price before discount (cost + margin, truncated, or the manual price)
     * @param discount        flat discount applied to the sale price
     * @param fuelSurcharge   pass-through
     * @param taxesAndCharges pass-through
     * @param cost            total cost behind the sale price; null when the price is a manual one
     */
    record Quote(String targetType, long salePrice, long discount, long fuelSurcharge, long taxesAndCharges,
                 boolean salesAvailable, Long cost) {

        public long passThrough() {
            return fuelSurcharge + taxesAndCharges;
        }

        /** Sale price after the discount: the base for commission and profit. */
        public long afterDiscount() {
            return Math.max(0, salePrice - discount);
        }

        /** Per-person total before discount. */
        public long totalBeforeDiscount() {
            return salePrice + passThrough();
        }

        /** What the customer pays per person: (sale price - discount) + pass-through. */
        public long payable() {
            return afterDiscount() + passThrough();
        }

        /** Sale price minus cost; null for manual prices. */
        public Long margin() {
            return cost == null ? null : salePrice - cost;
        }
    }
}
