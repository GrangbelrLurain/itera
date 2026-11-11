package aerocore.backend.service;

import aerocore.backend.model.ProductData;

import java.time.LocalDate;

/**
 * Prices a traveler type from cost when the product has cost rows for it (date overrides applied):
 * salePrice = truncate100(cost + cost * marginBp / 10000 + marginFixed). Otherwise the manual base price is used.
 * The discount never applies to infants; fuel surcharge and taxes are pass-through (no margin).
 */
public class CostBasedPriceCalculator implements PriceCalculator {

    @Override
    public Quote quote(ProductData product, LocalDate departureDate, String targetType) {
        ProductData.Price price = null;
        for (ProductData.Price p : product.prices()) {
            if (p.targetType().equals(targetType)) {
                price = p;
                break;
            }
        }
        if (price == null) return null;

        Long cost = product.pricing() == null ? null : product.pricing().costOf(targetType, departureDate);
        long sale;
        if (cost != null) {
            sale = PricingRules.salePrice(cost, product.pricing().marginBp(), product.pricing().marginFixed());
        } else {
            sale = price.basePrice(); // manual price for this traveler type
        }
        long discount = "INFANT".equals(targetType) ? 0 : Math.min(sale, Math.max(0, product.product().discountAmount()));
        return new Quote(targetType, sale, discount, price.fuelSurcharge(), price.taxesAndCharges(),
                price.salesAvailable(), cost);
    }
}
