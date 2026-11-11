package aerocore.backend.controller;

import aerocore.backend.config.MemoryData;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test hook of the memory profile: puts the sample data back. It is unauthenticated, so it exists only when
 * {@code aerocore.memory.reset-endpoint=true} (env MEMORY_RESET_ENDPOINT) is set explicitly: local runs and the end-to-end
 * tests turn it on, a public demo leaves it off. It never exists with a real database.
 */
@RestController
@Profile("memory")
@ConditionalOnProperty(name = "aerocore.memory.reset-endpoint", havingValue = "true")
@Hidden
@RequestMapping("/api/test")
public class MemoryResetController {

    private final MemoryData data;

    public MemoryResetController(MemoryData data) {
        this.data = data;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        data.reset();
    }
}
