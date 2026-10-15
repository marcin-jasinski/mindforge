package dev.mindforge.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mindForgeOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("MindForge API")
                .version("1.0")
                .description("AI-powered learning platform REST API"));
    }
}
