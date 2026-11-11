package aerocore.backend.controller;

import aerocore.backend.config.MemoryData;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Test hook of the memory profile: puts the sample data back. Does not exist with a real database. */
@RestController
@Profile("memory")
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
