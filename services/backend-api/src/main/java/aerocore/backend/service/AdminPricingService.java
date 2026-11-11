package aerocore.backend.service;

import aerocore.backend.dto.AdminPricingDtos.CommissionInput;
import aerocore.backend.dto.AdminPricingDtos.CostInput;
import aerocore.backend.dto.AdminPricingDtos.CostOverrideView;
import aerocore.backend.dto.AdminPricingDtos.PreviewRow;
import aerocore.backend.dto.AdminPricingDtos.PricingInput;
import aerocore.backend.dto.AdminPricingDtos.PricingPreview;
import aerocore.backend.dto.AdminPricingDtos.PricingView;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.ChannelCommission;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.model.ProductData;
import aerocore.backend.repository.ChannelCommissionRepository;
import aerocore.backend.repository.ProductAdminRepository;
import aerocore.backend.repository.ProductRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Back-office management of cost-based pricing: cost components and mark-up per product, commission per sales
 * channel, and a preview of the resulting prices, commission and profit.
 */
public class AdminPricingService {

    static final int MAX_COST_ROWS = 100;
    static final int MAX_COMMISSION_ROWS = 200;
    static final int MAX_MARGIN_BP = 100_000;     // 1000 %
    static final long MAX_AMOUNT = 1_000_000_000L;

    private static final Set<String> COMPONENTS = Set.of("FLIGHT", "HOTEL", "LAND", "TOUR", "INSURANCE", "OTHER");
    private static final Set<String> TARGETS = Set.of("ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT");
    private static final Pattern CHANNEL = Pattern.compile("^[A-Z0-9_]{1,30}$");

    private final ProductRepository read;
    private final ProductAdminRepository write;
    private final ChannelCommissionRepository commissions;
    private final PriceCalculator calculator;
    private final Clock clock;

    public AdminPricingService(ProductRepository read, ProductAdminRepository write,
                               ChannelCommissionRepository commissions, PriceCalculator calculator, Clock clock) {
        this.read = read;
        this.write = write;
        this.commissions = commissions;
        this.calculator = calculator;
        this.clock = clock;
    }

    // ---- product pricing -------------------------------------------------------------------

    public PricingView getPricing(String code) {
        ProductData d = requireProduct(code);
        ProductData.Pricing p = d.pricing();
        if (p == null) return new PricingView(List.of(), 0, 0);
        return new PricingView(p.costs().stream()
                .map(c -> new CostInput(c.componentType(), c.targetType(), c.cost(), c.note())).toList(),
                p.marginBp(), p.marginFixed());
    }

    public PricingView savePricing(String code, PricingInput in) {
        ProductData d = requireProduct(code);
        List<CostInput> costs = in == null || in.costs() == null ? List.of() : in.costs();
        if (costs.size() > MAX_COST_ROWS) throw new ValidationException("at most " + MAX_COST_ROWS + " cost rows are allowed");
        int marginBp = in == null || in.marginBp() == null ? 0 : in.marginBp();
        long marginFixed = in == null || in.marginFixed() == null ? 0 : in.marginFixed();
        if (marginBp < 0 || marginBp > MAX_MARGIN_BP) {
            throw new ValidationException("marginBp must be between 0 and " + MAX_MARGIN_BP);
        }
        if (marginFixed < 0 || marginFixed > MAX_AMOUNT) throw new ValidationException("marginFixed must be between 0 and " + MAX_AMOUNT);

        List<ProductData.CostRow> rows = toCostRows(d, costs, "costs", false);
        write.replacePricing(code, rows, marginBp, marginFixed);
        return getPricing(code);
    }

    /** Validates cost rows; {@code uniquePairs} rejects two rows with the same (component, target). */
    private static List<ProductData.CostRow> toCostRows(ProductData d, List<CostInput> costs, String label,
                                                        boolean uniquePairs) {
        Set<String> pricedTargets = new LinkedHashSet<>();
        d.prices().forEach(p -> pricedTargets.add(p.targetType()));
        Set<String> seen = new LinkedHashSet<>();
        List<ProductData.CostRow> rows = new ArrayList<>();
        for (int i = 0; i < costs.size(); i++) {
            CostInput c = costs.get(i);
            String who = label + "[" + i + "]";
            if (c == null || c.componentType() == null || !COMPONENTS.contains(c.componentType())) {
                throw new ValidationException(who + ".componentType must be one of " + COMPONENTS);
            }
            if (c.targetType() == null || !TARGETS.contains(c.targetType())) {
                throw new ValidationException(who + ".targetType must be one of " + TARGETS);
            }
            if (!pricedTargets.contains(c.targetType())) {
                throw new ValidationException(who + ": the product has no " + c.targetType()
                        + " price row; add the traveler type in the basic data first (it carries the fuel surcharge and taxes)");
            }
            if (c.cost() == null || c.cost() < 0 || c.cost() > MAX_AMOUNT) {
                throw new ValidationException(who + ".cost must be between 0 and " + MAX_AMOUNT);
            }
            if (uniquePairs && !seen.add(c.componentType() + "|" + c.targetType())) {
                throw new ValidationException(who + ": duplicate " + c.componentType() + " / " + c.targetType());
            }
            String note = c.note() == null || c.note().isBlank() ? null : c.note().trim();
            if (note != null && note.length() > 100) throw new ValidationException(who + ".note is too long (max 100)");
            rows.add(new ProductData.CostRow(i + 1, c.componentType(), c.targetType(), c.cost(), note));
        }
        return rows;
    }

    // ---- date-specific cost overrides ------------------------------------------------------

    public CostOverrideView getCostOverrides(String code, LocalDate date) {
        ProductData d = requireDeparture(code, date);
        return costOverrideView(d, date);
    }

    /**
     * Replaces the cost overrides of a departure date (an empty list reverts the date to the product's costs).
     * The date must be a departure; saving pins its departure row (storage rule S2).
     */
    public CostOverrideView saveCostOverrides(String code, LocalDate date, List<CostInput> in) {
        ProductData d = requireDeparture(code, date);
        List<CostInput> rows = in == null ? List.of() : in;
        if (rows.size() > MAX_COST_ROWS) throw new ValidationException("at most " + MAX_COST_ROWS + " override rows are allowed");
        write.replaceCostOverrides(code, date, toCostRows(d, rows, "overrides", true), d.product().defaultCapacity());
        return costOverrideView(requireProduct(code), date);
    }

    private CostOverrideView costOverrideView(ProductData d, LocalDate date) {
        ProductData.Pricing p = d.pricing();
        List<CostInput> base = p == null ? List.of() : p.costs().stream()
                .map(c -> new CostInput(c.componentType(), c.targetType(), c.cost(), c.note())).toList();
        List<CostInput> overrides = p == null ? List.of() : p.overrides().getOrDefault(date, List.of()).stream()
                .map(c -> new CostInput(c.componentType(), c.targetType(), c.cost(), c.note())).toList();
        return new CostOverrideView(date.toString(), base, overrides);
    }

    private ProductData requireDeparture(String code, LocalDate date) {
        ProductData d = requireProduct(code);
        if (date == null || !DepartureCalculator.isDeparture(d, read.findDepartureRows(code), date, BusinessTime.today(clock))) {
            throw new PackageNotFoundException(code, date);
        }
        return d;
    }

    /** Prices, commission and profit per traveler type for a sales channel (blank = DIRECT). */
    public PricingPreview preview(String code, String channelCode) {
        return preview(code, channelCode, null);
    }

    /** Same as above for one departure date: the date's cost overrides are applied (null = product costs only). */
    public PricingPreview preview(String code, String channelCode, LocalDate date) {
        ProductData d = requireProduct(code);
        LocalDate priceDate = date == null ? BusinessTime.today(clock) : date;
        String channel = channelCode == null || channelCode.isBlank()
                ? PackageBookingData.DIRECT_CHANNEL : channelCode.trim().toUpperCase();
        if (!CHANNEL.matcher(channel).matches()) throw new ValidationException("channel must be 1-30 characters of A-Z, 0-9 or _");
        Optional<ChannelCommission> policy = commissions.resolve(channel, code);

        List<PreviewRow> rows = new ArrayList<>();
        for (ProductData.Price price : d.prices()) {
            PriceCalculator.Quote q = calculator.quote(d, priceDate, price.targetType());
            if (q == null) continue;
            Long baseCost = d.pricing() == null ? null : d.pricing().costOf(price.targetType());
            boolean overridden = date != null && !java.util.Objects.equals(baseCost, q.cost());
            long commission = policy.map(c -> PricingRules.commission(q.afterDiscount(), c.commissionBp(),
                    c.commissionFixed())).orElse(0L);
            Long profit = q.cost() == null ? null : q.afterDiscount() - q.cost() - commission;
            rows.add(new PreviewRow(q.targetType(), q.cost() == null ? "MANUAL" : "COST", q.cost(), q.margin(),
                    q.salePrice(), q.discount(), q.afterDiscount(), q.passThrough(), q.payable(), commission, profit,
                    q.salesAvailable(), overridden));
        }
        return new PricingPreview(channel, date == null ? null : date.toString(),
                policy.map(ChannelCommission::commissionBp).orElse(null),
                policy.map(ChannelCommission::commissionFixed).orElse(null), rows);
    }

    // ---- channel commissions ---------------------------------------------------------------

    public List<CommissionInput> listCommissions() {
        return commissions.findAll().stream()
                .map(c -> new CommissionInput(c.channelCode(), c.productCode(), c.commissionBp(),
                        c.commissionFixed(), c.active()))
                .toList();
    }

    public List<CommissionInput> replaceCommissions(List<CommissionInput> in) {
        List<CommissionInput> rows = in == null ? List.of() : in;
        if (rows.size() > MAX_COMMISSION_ROWS) throw new ValidationException("at most " + MAX_COMMISSION_ROWS + " rows are allowed");
        List<ChannelCommission> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            CommissionInput r = rows.get(i);
            String who = "commissions[" + i + "]";
            if (r == null || r.channelCode() == null) throw new ValidationException(who + ".channelCode is required");
            String channel = r.channelCode().trim().toUpperCase();
            if (!CHANNEL.matcher(channel).matches()) {
                throw new ValidationException(who + ".channelCode must be 1-30 characters of A-Z, 0-9 or _");
            }
            String product = r.productCode() == null || r.productCode().isBlank() ? null : r.productCode().trim().toUpperCase();
            if (product != null && read.findProduct(product).isEmpty()) {
                throw new ValidationException(who + ".productCode: unknown product " + product);
            }
            int bp = r.commissionBp() == null ? 0 : r.commissionBp();
            long fixed = r.commissionFixed() == null ? 0 : r.commissionFixed();
            if (bp < 0 || bp > 10_000) throw new ValidationException(who + ".commissionBp must be between 0 and 10000");
            if (fixed < 0 || fixed > MAX_AMOUNT) throw new ValidationException(who + ".commissionFixed must be between 0 and " + MAX_AMOUNT);
            if (!seen.add(channel + "|" + (product == null ? "*" : product))) {
                throw new ValidationException(who + ": duplicate row for " + channel + " / " + (product == null ? "all products" : product));
            }
            out.add(new ChannelCommission(channel, product, bp, fixed, !Boolean.FALSE.equals(r.active())));
        }
        commissions.replaceAll(out);
        return listCommissions();
    }

    private ProductData requireProduct(String code) {
        return read.findProduct(code).orElseThrow(() -> new PackageNotFoundException(code, null));
    }
}
