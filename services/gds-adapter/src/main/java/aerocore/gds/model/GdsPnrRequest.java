package aerocore.gds.model;

/**
 * Request payload for creating a PNR (Passenger Name Record) in the GDS.
 */
public class GdsPnrRequest {
    private final String flightNumber;
    private final String departureDate;
    private final String passengerName;
    private final String passportNumber;
    private final String seatClass;
    private final long totalAmount;

    public GdsPnrRequest(String flightNumber, String departureDate, String passengerName, 
                         String passportNumber, String seatClass, long totalAmount) {
        this.flightNumber = flightNumber;
        this.departureDate = departureDate;
        this.passengerName = passengerName;
        this.passportNumber = passportNumber;
        this.seatClass = seatClass != null ? seatClass : "Y"; // Default economy
        this.totalAmount = totalAmount;
    }

    public String getFlightNumber() { return flightNumber; }
    public String getDepartureDate() { return departureDate; }
    public String getPassengerName() { return passengerName; }
    public String getPassportNumber() { return passportNumber; }
    public String getSeatClass() { return seatClass; }
    public long getTotalAmount() { return totalAmount; }

    @Override
    public String toString() {
        return "GdsPnrRequest{" +
                "flightNumber='" + flightNumber + '\'' +
                ", departureDate='" + departureDate + '\'' +
                ", passengerName='" + passengerName + '\'' +
                ", seatClass='" + seatClass + '\'' +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
