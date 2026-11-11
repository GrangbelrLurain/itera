package aerocore.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateBookingRequest {
    private String flightNumber;
    private String departureDate;
    private String passengerName;
    private String passengerPassportNumber;
    private Long totalAmount;

    public CreateBookingRequest() {}

    public CreateBookingRequest(String flightNumber,
                                String departureDate,
                                String passengerName,
                                String passengerPassportNumber,
                                Long totalAmount) {
        this.flightNumber = flightNumber;
        this.departureDate = departureDate;
        this.passengerName = passengerName;
        this.passengerPassportNumber = passengerPassportNumber;
        this.totalAmount = totalAmount;
    }

    public String getFlightNumber() { return flightNumber; }
    @JsonAlias("flight_number")
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getDepartureDate() { return departureDate; }
    @JsonAlias("departure_date")
    public void setDepartureDate(String departureDate) { this.departureDate = departureDate; }

    public String getPassengerName() { return passengerName; }
    @JsonAlias("passenger_name")
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPassportNumber() { return passengerPassportNumber; }
    @JsonAlias("passenger_passport_number")
    public void setPassengerPassportNumber(String passengerPassportNumber) { this.passengerPassportNumber = passengerPassportNumber; }

    public Long getTotalAmount() { return totalAmount; }
    @JsonAlias("total_amount")
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }
}
