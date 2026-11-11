package aerocore.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** AeroCore backend API (Spring Boot). Configuration comes from environment variables, see application.properties. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BackendApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApiApplication.class, args);
    }
}
