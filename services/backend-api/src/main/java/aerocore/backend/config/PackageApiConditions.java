package aerocore.backend.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** When the package, booking and admin services exist: a database is configured, or the memory profile is on. */
final class PackageApiConditions {

    private PackageApiConditions() {}

    static boolean memoryProfile(ConditionContext context) {
        return context.getEnvironment().acceptsProfiles(Profiles.of("memory"));
    }

    static boolean databaseConfigured(ConditionContext context) {
        String url = context.getEnvironment().getProperty("aerocore.db.url");
        return url != null && !url.isBlank();
    }

    /** Package and booking services. */
    static final class Enabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return databaseConfigured(context) || memoryProfile(context);
        }
    }

    /** Admin services: additionally needs an admin password (there is no built-in default). */
    static final class AdminEnabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String password = context.getEnvironment().getProperty("aerocore.admin.password");
            return new Enabled().matches(context, metadata) && password != null && !password.isEmpty();
        }
    }

    /** Jdbc repositories: only for a real database, never together with the memory profile. */
    static final class Jdbc implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return databaseConfigured(context) && !memoryProfile(context);
        }
    }
}
