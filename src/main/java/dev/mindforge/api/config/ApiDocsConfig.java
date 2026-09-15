package dev.mindforge.api.config;

import java.util.ArrayList;

import io.swagger.v3.core.converter.ModelConverters;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mindforge.api.dto.response.ErrorResponse;

/**
 * Shapes the OpenAPI document the SPA's types are generated from. A response record serializes every component, null
 * or not, so each is marked required; the error shape omits nulls, so its optional fields stay optional. Only
 * exception handlers return that shape, so it is published here.
 */
@Configuration
public class ApiDocsConfig {

    private static final String RESPONSE_SUFFIX = "Response";

    @Bean
    OpenApiCustomizer responseSchemas() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.endsWith(RESPONSE_SUFFIX) && !name.equals(ErrorResponse.class.getSimpleName())
                    && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }
}
