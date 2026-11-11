package aerocore.backend.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A master product with everything that is the same for every departure date.
 * Departure dates are computed from {@code rules}/{@code exclusions} (see DepartureCalculator).
 */
public record ProductData(
        Product product,
        List<Price> prices,
        List<Rule> rules,
        Set<LocalDate> exclusions,
        List<ItineraryRow> itinerary,
        List<OptionalTourRow> optionalTours,
        List<ShoppingRow> shoppings,
        Remark remark,
        Condition condition,
        Pricing pricing) {

    /** Products without cost data (pricing == null) use their manual base prices. */
    public ProductData(Product product, List<Price> prices, List<Rule> rules, Set<LocalDate> exclusions,
                       List<ItineraryRow> itinerary, List<OptionalTourRow> optionalTours,
                       List<ShoppingRow> shoppings, Remark remark, Condition condition) {
        this(product, prices, rules, exclusions, itinerary, optionalTours, shoppings, remark, condition, null);
    }

    /**
     * Cost-based pricing data (docs/design/package-pricing.md). marginBp is in 1/100 %.
     * A traveler type with at least one cost row is priced from cost; the others keep their manual base price.
     */
    public record Pricing(List<CostRow> costs, int marginBp, long marginFixed,
                          Map<LocalDate, List<CostRow>> overrides) {

        /** Pricing without date-specific cost adjustments. */
        public Pricing(List<CostRow> costs, int marginBp, long marginFixed) {
            this(costs, marginBp, marginFixed, Map.of());
        }

        /** Sum of the product's cost rows of one traveler type, or null when there are none. */
        public Long costOf(String targetType) {
            return sum(costs, targetType);
        }

        /**
         * Cost of one traveler type on a departure date: the product's rows, where a date override with the same
         * (component, target) replaces the product's cost and any other override row adds a cost line.
         * Null when the type has no cost rows at all on that date.
         */
        public Long costOf(String targetType, LocalDate date) {
            List<CostRow> adjust = overrides.getOrDefault(date, List.of());
            if (adjust.isEmpty()) return costOf(targetType);
            Map<String, Long> byComponent = new LinkedHashMap<>();
            for (CostRow c : costs) {
                if (c.targetType().equals(targetType)) byComponent.merge(c.componentType(), c.cost(), Long::sum);
            }
            Map<String, Boolean> replaced = new LinkedHashMap<>();
            for (CostRow o : adjust) {
                if (!o.targetType().equals(targetType)) continue;
                if (replaced.put(o.componentType(), true) == null) byComponent.remove(o.componentType());
                byComponent.merge(o.componentType(), o.cost(), Long::sum);
            }
            if (byComponent.isEmpty()) return null;
            long total = 0;
            for (long v : byComponent.values()) total += v;
            return total;
        }

        private static Long sum(List<CostRow> rows, String targetType) {
            long sum = 0;
            boolean any = false;
            for (CostRow c : rows) {
                if (c.targetType().equals(targetType)) {
                    sum += c.cost();
                    any = true;
                }
            }
            return any ? sum : null;
        }
    }

    /** componentType: FLIGHT, HOTEL, LAND, TOUR, INSURANCE or OTHER. */
    public record CostRow(int seq, String componentType, String targetType, long cost, String note) {}

    public record Product(
            String code, String name, String grade, int nights, int days,
            String departureRoute, String departureTime, String returnRoute, String returnTime,
            boolean directFlight, boolean visaRequired, String meetingTime, String meetingLocation,
            String hotelName, Integer hotelStars, String highlight, String promotionTitle,
            long discountAmount, int defaultCapacity, boolean active) {}

    /** Base price per traveler type; fuel surcharge and taxes are pass-through (no margin). */
    public record Price(String targetType, long basePrice, long fuelSurcharge, long taxesAndCharges,
                        boolean salesAvailable) {}

    public record Rule(long ruleId, LocalDate validFrom, LocalDate validTo, Set<DayOfWeek> weekdays, boolean active) {}

    public record ItineraryRow(int dayNo, int sortOrder, String serviceType, String placeName, String description,
                               boolean highlight, boolean perk) {}

    public record OptionalTourRow(int seq, String name, String placeName, String nameEn, String summary,
                                  String details, String currency, Double priceAdult, Double priceChild,
                                  Integer minParticipants, Integer durationMinutes, Boolean included,
                                  Boolean preSale) {}

    public record ShoppingRow(int seq, String itemName, String placeName, Integer durationMinutes,
                              boolean refundable) {}

    public record Remark(String priceNote, String inclusions, String exclusions, String highlights,
                         String bookingNotice, String roomInfo, String travelNotice, String faq) {}

    public record Condition(String airlineSeatClass, boolean tourLeader, String localGuide,
                            boolean insuranceIncluded, int shoppingCount, Boolean optionalTourIncluded,
                            boolean admissionFeeIncluded, String localExpensesCurrency, int localExpensesAdult,
                            int localExpensesChild, int localExpensesInfant, int singleRoomFee,
                            int minDeparturePopulation, String cancellationPolicy) {}
}
