package aerocore.backend.config;

import aerocore.backend.repository.ChannelCommissionRepository;
import aerocore.backend.repository.PackageBookingRepository;
import aerocore.backend.repository.ProductAdminRepository;
import aerocore.backend.repository.ProductRepository;
import aerocore.backend.service.AdminAuthService;
import aerocore.backend.service.AdminPricingService;
import aerocore.backend.service.AdminProductService;
import aerocore.backend.service.AdminService;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageBookingService;
import aerocore.backend.service.PackageService;
import aerocore.backend.service.PriceCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Package, booking and admin services. They sit on whichever repositories exist: the SQL ones (DatabaseConfig)
 * or the in-memory ones (MemoryConfig, profile "memory").
 */
@Configuration
@Conditional(PackageApiConditions.Enabled.class)
public class PackageApiConfig {

    @Bean
    PriceCalculator priceCalculator() {
        return new CostBasedPriceCalculator();
    }

    @Bean
    PackageService packageService(ProductRepository products, PriceCalculator calculator, Clock clock) {
        return new PackageService(products, calculator, clock);
    }

    @Bean
    PackageBookingService packageBookingService(ProductRepository products, PackageBookingRepository bookings,
                                                PriceCalculator calculator, ChannelCommissionRepository commissions,
                                                Clock clock) {
        return new PackageBookingService(products, bookings, calculator, commissions, clock);
    }

    /**
     * Admin API: enabled only when an admin password is configured. Nested configuration classes are also found
     * by component scanning on their own, so the conditions are repeated here.
     */
    @Configuration
    @Conditional(PackageApiConditions.AdminEnabled.class)
    static class AdminConfig {

        @Bean
        AdminAuthService adminAuthService(AdminProperties admin, Clock clock) {
            String user = admin.username() == null || admin.username().isBlank() ? "admin" : admin.username().trim();
            String secret = admin.tokenSecret() == null || admin.tokenSecret().isEmpty() ? null : admin.tokenSecret();
            return new AdminAuthService(user, admin.password(), secret, clock);
        }

        @Bean
        AdminService adminService(PackageBookingService bookingService, PackageBookingRepository bookings,
                                  PackageService packages, Clock clock) {
            return new AdminService(bookingService, bookings, packages, clock);
        }

        @Bean
        AdminProductService adminProductService(ProductRepository read, ProductAdminRepository write, Clock clock) {
            return new AdminProductService(read, write, clock);
        }

        @Bean
        AdminPricingService adminPricingService(ProductRepository read, ProductAdminRepository write,
                                                ChannelCommissionRepository commissions, PriceCalculator calculator,
                                                Clock clock) {
            return new AdminPricingService(read, write, commissions, calculator, clock);
        }
    }
}
