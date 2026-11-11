package aerocore.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One departure (product + date) of a package. Dates are ISO strings (yyyy-MM-dd).
 * Fields marked {@link Nullable} (or boxed) can be null; every other field is always present.
 */
@Schema(name = "PackageDetail")
public record PackageDetailResponse(
        Basic basic,
        List<Price> prices,
        List<OptionalTour> optionalTours,
        List<Shopping> shoppings,
        Itinerary itinerary,
        @Nullable Remark remark,
        @Nullable Condition condition) {

    /** status: OPEN (bookable), FULL (no seats left), CLOSED (closed by an admin). */
    @Schema(name = "PackageBasic")
    public record Basic(
            String productCode, @Schema(format = "date") String departureDate, String name,
            @Nullable String grade, @Nullable String promotionTitle,
            @Schema(allowableValues = {"OPEN", "FULL", "CLOSED"}) String status, boolean isStoredDeparture,
            String departureInfo, String arrivalInfo, int nights, int days,
            boolean isDirectFlight, boolean isVisaRequired,
            @Nullable String meetingTime, @Nullable String meetingLocation, @Nullable String hotelName,
            Integer hotelStars,
            @Schema(format = "date") String freeCancellationUntil,
            int capacity, int reservedSeats, int remainingSeats,
            @Nullable String highlight) {}

    @Schema(name = "PackagePrice")
    public record Price(
            @Schema(allowableValues = {"ADULT", "NOBED_CHILD", "EXTRABED_CHILD", "INFANT"}) String targetType,
            long salePrice, long discount, long fuelSurcharge, long taxesAndCharges,
            long totalPrice, long discountedTotalPrice, boolean isSalesAvailable) {}

    @Schema(name = "PackageOptionalTour")
    public record OptionalTour(int seq, String name, @Nullable String placeName, @Nullable String nameEn,
                               @Nullable String summary, @Nullable String details, @Nullable String currency,
                               Double priceAdult, Double priceChild, Integer minParticipants,
                               Integer durationMinutes, Boolean isIncluded, Boolean isPreSale) {}

    @Schema(name = "PackageShopping")
    public record Shopping(int seq, String itemName, @Nullable String placeName, Integer durationMinutes,
                           boolean isRefundable) {}

    @Schema(name = "PackageItinerary")
    public record Itinerary(List<Day> days) {}

    @Schema(name = "ItineraryDay")
    public record Day(int day, @Schema(format = "date") String date, List<Item> items) {}

    @Schema(name = "ItineraryItem")
    public record Item(@Schema(allowableValues = {"FLIGHT", "SIGHTSEEING", "MEAL", "HOTEL", "FREE"}) String serviceType,
                       @Nullable String placeName, @Nullable String description, boolean isPerk,
                       boolean isHighlight) {}

    @Schema(name = "PackageRemark")
    public record Remark(@Nullable String priceNote, @Nullable String inclusions, @Nullable String exclusions,
                         @Nullable String highlights, @Nullable String bookingNotice, @Nullable String roomInfo,
                         @Nullable String travelNotice, @Nullable String faq) {}

    @Schema(name = "PackageCondition")
    public record Condition(@Nullable String airlineSeatClass, boolean hasTourLeader, @Nullable String localGuide,
                            boolean isInsuranceIncluded, int shoppingCount, Boolean isOptionalTourIncluded,
                            boolean isAdmissionFeeIncluded, @Nullable String localExpensesCurrency,
                            int localExpensesAdult, int localExpensesChild, int localExpensesInfant,
                            int singleRoomFee, int minDeparturePopulation, @Nullable String cancellationPolicy) {}
}
