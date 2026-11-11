package aerocore.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SQL Server connection (env DB_URL / DB_USER / DB_PASSWORD). Without a url the package/admin APIs are disabled. */
@ConfigurationProperties(prefix = "aerocore.db")
public record DbProperties(String url, String user, String password) {

    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
