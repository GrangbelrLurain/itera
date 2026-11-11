package aerocore.backend.config;

import aerocore.backend.repository.ChannelCommissionRepository;
import aerocore.backend.repository.JdbcChannelCommissionRepository;
import aerocore.backend.repository.JdbcPackageBookingRepository;
import aerocore.backend.repository.JdbcProductAdminRepository;
import aerocore.backend.repository.JdbcProductRepository;
import aerocore.backend.repository.PackageBookingRepository;
import aerocore.backend.repository.ProductAdminRepository;
import aerocore.backend.repository.ProductRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SQL Server repositories; only present when aerocore.db.url is set (and the memory profile is off). */
@Configuration
@Conditional(PackageApiConditions.Jdbc.class)
public class DatabaseConfig {

    @Bean
    ProductRepository productRepository(DbProperties db) {
        return new JdbcProductRepository(db.url().trim(), db.user(), db.password());
    }

    @Bean
    PackageBookingRepository packageBookingRepository(DbProperties db) {
        return new JdbcPackageBookingRepository(db.url().trim(), db.user(), db.password());
    }

    @Bean
    ChannelCommissionRepository channelCommissionRepository(DbProperties db) {
        return new JdbcChannelCommissionRepository(db.url().trim(), db.user(), db.password());
    }

    @Bean
    ProductAdminRepository productAdminRepository(DbProperties db) {
        return new JdbcProductAdminRepository(db.url().trim(), db.user(), db.password());
    }
}
