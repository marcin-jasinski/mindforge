package dev.mindforge.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Root application configuration: registers typed properties and enables JPA auditing. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaAuditing
public class AppConfig {}
