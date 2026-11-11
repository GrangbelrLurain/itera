package aerocore.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Puts the memory profile back to its sample data at a fixed interval (env MEMORY_RESET_INTERVAL, an ISO-8601
 * duration such as PT6H). For a public demo where anyone may change the data; off when the interval is blank.
 */
@Configuration
@Profile("memory")
@EnableScheduling
@ConditionalOnExpression("!'${aerocore.memory.reset-interval:}'.isBlank()")
public class MemoryResetSchedule {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryResetSchedule.class);

    private final MemoryData data;

    public MemoryResetSchedule(MemoryData data) {
        this.data = data;
    }

    @Scheduled(fixedRateString = "${aerocore.memory.reset-interval}", initialDelayString = "${aerocore.memory.reset-interval}")
    void reset() {
        data.reset();
        LOG.info("Memory profile reset to the sample data");
    }
}
