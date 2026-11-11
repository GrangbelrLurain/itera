package aerocore.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business "today" is the Korean calendar day, independent of the server's time zone. */
final class BusinessTime {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private BusinessTime() {}

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
