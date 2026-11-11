package aerocore.backend.dto;

public class HealthResponse {
    private String status;
    private String version;

    public HealthResponse() {
        this.status = "UP";
        this.version = "1.0.0-java";
    }

    public HealthResponse(String status, String version) {
        this.status = status;
        this.version = version;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
