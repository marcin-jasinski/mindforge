package dev.mindforge.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.domain.port.PasswordHasher;
import dev.mindforge.domain.port.UserRepository;
import dev.mindforge.infrastructure.security.BCryptPasswordHasher;
import dev.mindforge.infrastructure.security.JwtService;

/** Wires accounts, session tokens and knowledge-base ownership. */
@Configuration
public class AccountConfig {

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    JwtService jwtService(AppProperties properties) {
        AppProperties.Security security = properties.getSecurity();
        return new JwtService(security.getJwtSecret(), Duration.ofSeconds(security.getJwtExpirySeconds()),
            security.isSecureCookies());
    }

    @Bean
    AccountService accountService(UserRepository userRepository, PasswordHasher passwordHasher) {
        return new AccountService(userRepository, passwordHasher);
    }

    @Bean
    KnowledgeBaseService knowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository) {
        return new KnowledgeBaseService(knowledgeBaseRepository);
    }
}
