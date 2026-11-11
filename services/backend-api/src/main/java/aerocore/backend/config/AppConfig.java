package aerocore.backend.config;

import aerocore.backend.repository.BookingRepository;
import aerocore.backend.repository.InMemoryBookingRepository;
import aerocore.backend.repository.JdbcBookingRepository;
import aerocore.backend.service.BookingService;
import aerocore.backend.service.PdfTicketGenerator;
import aerocore.backend.service.SeatLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Beans that exist regardless of the database (the flight booking prototype and the clock). */
@Configuration
public class AppConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BookingRepository bookingRepository(DbProperties db) {
        if (!db.configured()) {
            LOG.info("aerocore.db.url not set: using in-memory booking storage");
            return new InMemoryBookingRepository();
        }
        return new JdbcBookingRepository(db.url().trim(), db.user(), db.password());
    }

    @Bean
    BookingService bookingService(BookingRepository repository) {
        return new BookingService(new SeatLockManager(), new PdfTicketGenerator(), repository);
    }
}
