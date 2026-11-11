package aerocore.backend.service;

/**
 * The arithmetic of docs/design/package-pricing.md. Pure integer math (rates are bp = 1/100 %), no floating point.
 * Amounts are won. Every computed amount is truncated to 100 won.
 */
public final class PricingRules {

    public static final long ROUND_UNIT = 100;

    private PricingRules() {}

    /** Truncates (rounds toward zero) to {@link #ROUND_UNIT}; negative inputs are treated as 0. */
    public static long truncate(long amount) {
        return amount <= 0 ? 0 : (amount / ROUND_UNIT) * ROUND_UNIT;
    }

    /**
     * Sale price from cost: mark-up (cost-based percentage) plus a fixed margin, truncated to 100 won.
     * markedUp = cost + cost * marginBp / 10000 + marginFixed.
     */
    public static long salePrice(long cost, int marginBp, long marginFixed) {
        long markedUp = cost + cost * marginBp / 10_000 + marginFixed;
        return truncate(markedUp);
    }

    /**
     * Channel commission per person: afterDiscountSale * bp / 10000 + fixed, never more than the sale price
     * it is taken from, truncated to 100 won.
     */
    public static long commission(long afterDiscountSale, int commissionBp, long commissionFixed) {
        long raw = afterDiscountSale * commissionBp / 10_000 + commissionFixed;
        return truncate(Math.min(afterDiscountSale, raw));
    }
}
