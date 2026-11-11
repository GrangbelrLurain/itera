package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Admin product management shapes (master product, schedule, itinerary, extras, departures).
 * The same records are used to read and to write, so a form can be loaded, edited and saved as-is.
 * Dates are ISO strings (yyyy-MM-dd). Keep in sync with services/backend-api/openapi.yaml.
 */
public final class AdminProductDtos {

    private AdminProductDtos() {}

    /** Basic product data plus per-traveler-type base prices (PUT /admin/products/{code}). */
    public record ProductInput(
            String code, String name, @Nullable String grade, Integer nights, Integer days,
            @Nullable String departureRoute, @Nullable String departureTime, @Nullable String returnRoute, @Nullable String returnTime,
            Boolean directFlight, Boolean visaRequired, @Nullable String meetingTime, @Nullable String meetingLocation,
            @Nullable String hotelName, @Nullable Integer hotelStars, @Nullable String highlight, @Nullable String promotionTitle,
            Long discountAmount, Integer defaultCapacity, Boolean active, List<PriceInput> prices) {}

    /** targetType: ADULT, NOBED_CHILD, EXTRABED_CHILD or INFANT. */
    public record PriceInput(@Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType, Long basePrice, Long fuelSurcharge, Long taxesAndCharges,
                             Boolean salesAvailable) {}

    /** serviceType: FLIGHT, SIGHTSEEING, MEAL, HOTEL or FREE. dayNo starts at 1. */
    public record ItineraryInput(Integer dayNo,
                                 @Schema(allowableValues = {"FLIGHT", "SIGHTSEEING", "MEAL", "HOTEL", "FREE"}) String serviceType, @Nullable String placeName, @Nullable String description,
                                 Boolean highlight, Boolean perk) {}

    /** weekdays: MON..SUN. */
    public record RuleInput(String validFrom, String validTo, List<String> weekdays, Boolean active) {}

    public record ScheduleInput(List<RuleInput> rules, List<String> exclusions) {}

    public record OptionalTourInput(String name, @Nullable String placeName, @Nullable String nameEn, @Nullable String summary, @Nullable String details,
                                    @Nullable String currency, @Nullable Double priceAdult, @Nullable Double priceChild, @Nullable Integer minParticipants,
                                    @Nullable Integer durationMinutes, @Nullable Boolean included, @Nullable Boolean preSale) {}

    public record ShoppingInput(String itemName, @Nullable String placeName, @Nullable Integer durationMinutes, Boolean refundable) {}

    public record RemarkInput(@Nullable String priceNote, @Nullable String inclusions, @Nullable String exclusions, @Nullable String highlights,
                              @Nullable String bookingNotice, @Nullable String roomInfo, @Nullable String travelNotice, @Nullable String faq) {}

    public record ConditionInput(@Nullable String airlineSeatClass, Boolean tourLeader, @Nullable String localGuide,
                                 Boolean insuranceIncluded, Integer shoppingCount, @Nullable Boolean optionalTourIncluded,
                                 Boolean admissionFeeIncluded, @Nullable String localExpensesCurrency,
                                 Integer localExpensesAdult, Integer localExpensesChild, Integer localExpensesInfant,
                                 Integer singleRoomFee, Integer minDeparturePopulation, @Nullable String cancellationPolicy) {}

    /** Tour options, shopping stops, notes and conditions (PUT /admin/products/{code}/extras). */
    public record ExtrasInput(List<OptionalTourInput> optionalTours, List<ShoppingInput> shoppings,
                              @Nullable RemarkInput remark, @Nullable ConditionInput condition) {}

    /** Everything an edit screen needs. */
    public record ProductDetail(ProductInput product, ScheduleInput schedule, List<ItineraryInput> itinerary,
                                ExtrasInput extras) {}

    public record ProductSummary(String code, String name, String grade, int nights, int days, boolean active,
                                 int defaultCapacity, String scheduleSummary) {}

    /**
     * One date in the admin departure calendar. status: OPEN, FULL, CLOSED or CANCELED.
     * fromRule: produced by a departure rule; stored: a departure row exists; pinned: kept by an admin action.
     */
    public record DepartureItem(@Schema(format = "date") String date,
                                @Schema(allowableValues = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"}) String weekday,
                                @Schema(allowableValues = {"OPEN", "FULL", "CLOSED", "CANCELED"}) String status, int capacity, int reservedSeats,
                                int remainingSeats, boolean stored, boolean pinned, boolean fromRule,
                                int costOverrides) {}

    public record CapacityRequest(@NotNull Integer capacity) {}

    public record DepartureDateRequest(@NotNull @Schema(format = "date") String date) {}
}
