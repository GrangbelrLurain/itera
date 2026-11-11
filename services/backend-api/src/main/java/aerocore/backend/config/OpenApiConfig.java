package aerocore.backend.config;

import aerocore.backend.dto.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The API contract is generated from the controllers and split in two documents:
 * public (customer apps) and admin (back office). Served at /v3/api-docs/{group}.
 * Schema precision (required / nullable) comes from {@link OpenApiSchemaConverter}.
 */
@Configuration
public class OpenApiConfig {

    public static final String PUBLIC_GROUP = "public";
    public static final String ADMIN_GROUP = "admin";

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group(PUBLIC_GROUP)
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/admin/**")
                .addOpenApiCustomizer(api -> api
                        .info(new Info().title("AeroCore Public API").version("1.0.0")
                                .description("Package browsing and booking for customer apps."))
                        .servers(List.of(new Server().url("/"))))
                .addOpenApiCustomizer(errorResponses(false))
                .build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group(ADMIN_GROUP)
                .pathsToMatch("/api/v1/admin/**")
                .addOpenApiCustomizer(api -> api
                        .servers(List.of(new Server().url("/")))
                        .info(new Info().title("AeroCore Admin API").version("1.0.0")
                                .description("Back office: products, departures, pricing, bookings."))
                        .addSecurityItem(new SecurityRequirement().addList("bearerAuth")))
                .addOpenApiCustomizer(api -> {
                    if (api.getComponents() == null) api.setComponents(new Components());
                    api.getComponents().addSecuritySchemes("bearerAuth",
                            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer"));
                })
                .addOpenApiCustomizer(errorResponses(true))
                .build();
    }

    /**
     * Every operation can answer with the common {error, code} body: documented as the `default` response, and
     * as 401 for the admin operations that need a token.
     */
    private static OpenApiCustomizer errorResponses(boolean admin) {
        return api -> {
            if (api.getComponents() == null) api.setComponents(new Components());
            ModelConverters.getInstance(true).read(ErrorResponse.class).forEach(api.getComponents()::addSchemas);

            Content json = new Content().addMediaType("application/json",
                    new MediaType().schema(new JsonSchema().$ref("#/components/schemas/ErrorResponse")));
            ApiResponse failure = new ApiResponse().description("Error (see the code field)").content(json);
            ApiResponse unauthenticated = new ApiResponse()
                    .description("Missing, expired or invalid bearer token (code UNAUTHENTICATED)").content(json);

            if (api.getPaths() == null) return;
            api.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                op.getResponses().addApiResponse("default", failure);
                if (admin && !"adminLogin".equals(op.getOperationId())) {
                    op.getResponses().addApiResponse("401", unauthenticated);
                }
            }));
        };
    }
}
