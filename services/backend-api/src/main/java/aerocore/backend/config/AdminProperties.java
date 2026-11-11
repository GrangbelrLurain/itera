package aerocore.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Single admin account (env ADMIN_USERNAME / ADMIN_PASSWORD / ADMIN_TOKEN_SECRET). There is no default password. */
@ConfigurationProperties(prefix = "aerocore.admin")
public record AdminProperties(String username, String password, String tokenSecret) {
}
