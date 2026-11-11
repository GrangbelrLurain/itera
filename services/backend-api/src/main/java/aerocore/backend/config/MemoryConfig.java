package aerocore.backend.config;

import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.repository.memory.MemoryCommissionRepository;
import aerocore.backend.repository.memory.MemoryProductStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import java.time.Clock;

/**
 * Profile "memory": the whole backend runs on in-memory repositories with sample data and no database.
 * For local runs and end-to-end tests; the data is lost on restart and can be reset through MemoryResetController.
 * Each store is one bean that implements the repository interfaces the services ask for.
 */
@Configuration
@Profile("memory")
public class MemoryConfig {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryConfig.class);

    @Bean
    MemoryProductStore memoryProductStore() {
        return new MemoryProductStore();
    }

    @Bean
    MemoryCommissionRepository memoryCommissionRepository() {
        return new MemoryCommissionRepository();
    }

    @Bean
    InMemoryPackageBookingRepository memoryPackageBookingRepository(MemoryProductStore store) {
        return new InMemoryPackageBookingRepository(store);
    }

    @Bean
    MemoryData memoryData(MemoryProductStore products, MemoryCommissionRepository commissions,
                          InMemoryPackageBookingRepository bookings, Clock clock) {
        return new MemoryData(products, commissions, bookings, clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    void seed(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(MemoryData.class).reset();
        LOG.warn("Profile 'memory': in-memory data with sample products. Not for production.");
    }
}
