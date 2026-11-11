package aerocore.backend.model;

/**
 * Commission paid to a sales channel (docs/design/package-pricing.md). productCode null = every product;
 * a row for a specific product overrides the channel's all-products row.
 * Rate in bp (1/100 %) plus an optional fixed amount per person; commission is taken from the sale price
 * after discount.
 */
public record ChannelCommission(String channelCode, String productCode, int commissionBp, long commissionFixed,
                                boolean active) {}
