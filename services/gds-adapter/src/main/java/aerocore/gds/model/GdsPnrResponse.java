package aerocore.gds.model;

import java.time.Instant;

/**
 * Response payload returned after PNR creation in the GDS.
 */
public class GdsPnrResponse {
    private final String pnrCode;
    private final GdsType gdsType;
    private final String airlineReference;
    private final String rawTelegramResponse;
    private final Instant createdAt;
    private final boolean success;

    public GdsPnrResponse(String pnrCode, GdsType gdsType, String airlineReference, 
                          String rawTelegramResponse, boolean success) {
        this.pnrCode = pnrCode;
        this.gdsType = gdsType;
        this.airlineReference = airlineReference;
        this.rawTelegramResponse = rawTelegramResponse;
        this.createdAt = Instant.now();
        this.success = success;
    }

    public String getPnrCode() { return pnrCode; }
    public GdsType getGdsType() { return gdsType; }
    public String getAirlineReference() { return airlineReference; }
    public String getRawTelegramResponse() { return rawTelegramResponse; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isSuccess() { return success; }

    @Override
    public String toString() {
        return "GdsPnrResponse{" +
                "pnrCode='" + pnrCode + '\'' +
                ", gdsType=" + gdsType +
                ", airlineRef='" + airlineReference + '\'' +
                ", success=" + success +
                '}';
    }
}
