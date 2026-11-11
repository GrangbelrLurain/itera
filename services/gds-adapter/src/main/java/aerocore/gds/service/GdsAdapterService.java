package aerocore.gds.service;

import aerocore.gds.adapter.GdsAdapterFactory;
import aerocore.gds.adapter.GdsAdapter;
import aerocore.gds.model.*;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * GdsAdapterService coordinates high-level GDS interactions: it picks the adapter for a flight
 * and exposes booking, ticketing and cancellation to the rest of the platform.
 */
public class GdsAdapterService {
    private static final Logger log = Logger.getLogger(GdsAdapterService.class.getName());
    private final GdsAdapterFactory adapterFactory;

    public GdsAdapterService() {
        this(new GdsAdapterFactory());
    }

    public GdsAdapterService(GdsAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
    }

    /**
     * Creates a PNR booking across the appropriate GDS.
     * Mirrors proto reservation.v1.CreateBookingRequest contract.
     */
    public GdsBookingResult createBooking(String flightNumber, String departureDate,
                                          String passengerName, String passportNumber,
                                          long totalAmount) {
        log.info(String.format("[GdsAdapterService] Received CreateBooking: Flight=%s, Date=%s, Pax=%s",
                flightNumber, departureDate, passengerName));

        if (passengerName == null || passengerName.isBlank()) {
            return GdsBookingResult.failed("Passenger name is required");
        }

        GdsAdapter adapter = adapterFactory.getAdapterForFlight(flightNumber);
        GdsPnrRequest request = new GdsPnrRequest(flightNumber, departureDate, passengerName, passportNumber, "Y", totalAmount);

        GdsPnrResponse pnrResp = adapter.reserveSeat(request);
        if (!pnrResp.isSuccess()) {
            return GdsBookingResult.failed("GDS reservation rejected: " + pnrResp.getRawTelegramResponse());
        }

        String bookingId = "BKG-" + flightNumber + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return GdsBookingResult.success(bookingId, pnrResp.getPnrCode(), pnrResp.getGdsType(), pnrResp.getAirlineReference());
    }

    /**
     * Issues an e-ticket across the GDS.
     * Mirrors proto reservation.v1.IssueTicketRequest contract.
     */
    public GdsTicketResult issueTicket(String pnrCode, String flightNumber, String paymentTxId, long amount) {
        log.info(String.format("[GdsAdapterService] Received IssueTicket: PNR=%s, Flight=%s, Tx=%s",
                pnrCode, flightNumber, paymentTxId));

        GdsAdapter adapter = adapterFactory.getAdapterForFlight(flightNumber);
        GdsTicketCommand cmd = new GdsTicketCommand(pnrCode, paymentTxId, amount, "SELKE08AA");

        return adapter.finalizeTicketing(cmd);
    }

    /**
     * Cancels an existing PNR across the GDS.
     */
    public boolean cancelBooking(String pnrCode, String flightNumber) {
        log.info(String.format("[GdsAdapterService] Cancelling PNR=%s, Flight=%s", pnrCode, flightNumber));
        GdsAdapter adapter = adapterFactory.getAdapterForFlight(flightNumber);
        return adapter.voidReservation(pnrCode);
    }

    /**
     * Composite DTO for booking result matching Protobuf responses.
     */
    public static class GdsBookingResult {
        private final boolean success;
        private final String bookingId;
        private final String pnrCode;
        private final GdsType gdsType;
        private final String airlineReference;
        private final String errorMessage;

        private GdsBookingResult(boolean success, String bookingId, String pnrCode,
                                 GdsType gdsType, String airlineReference, String errorMessage) {
            this.success = success;
            this.bookingId = bookingId;
            this.pnrCode = pnrCode;
            this.gdsType = gdsType;
            this.airlineReference = airlineReference;
            this.errorMessage = errorMessage;
        }

        public static GdsBookingResult success(String bookingId, String pnrCode, GdsType gdsType, String airlineRef) {
            return new GdsBookingResult(true, bookingId, pnrCode, gdsType, airlineRef, null);
        }

        public static GdsBookingResult failed(String error) {
            return new GdsBookingResult(false, null, null, null, null, error);
        }

        public boolean isSuccess() { return success; }
        public String getBookingId() { return bookingId; }
        public String getPnrCode() { return pnrCode; }
        public GdsType getGdsType() { return gdsType; }
        public String getAirlineReference() { return airlineReference; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return "GdsBookingResult{" +
                    "success=" + success +
                    ", bookingId='" + bookingId + '\'' +
                    ", pnrCode='" + pnrCode + '\'' +
                    ", gdsType=" + gdsType +
                    ", error='" + errorMessage + '\'' +
                    '}';
        }
    }
}
