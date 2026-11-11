package aerocore.backend.controller;

import aerocore.backend.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health")
public class HealthController {

    @GetMapping({"/api/v1/health", "/health"})
    @Operation(operationId = "getHealth", summary = "Liveness check")
    public HealthResponse getHealth() {
        return new HealthResponse("UP", "1.0.0-java");
    }
}
