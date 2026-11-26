package dev.mindforge.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.application.service.AccountService;
import dev.mindforge.application.service.ExternalIdentityService;
import dev.mindforge.application.service.KnowledgeBaseService;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.KnowledgeBaseRepository;
import dev.mindforge.domain.port.PasswordHasher;
import dev.mindforge.domain.port.QuizSessionStore;
import dev.mindforge.domain.port.UserRepository;
import dev.mindforge.infrastructure.security.BCryptPasswordHasher;
import dev.mindforge.infrastructure.security.JwtService;

/** Wires accounts, session tokens, knowledge-base ownership and the resolution of chat-platform users. */
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
    KnowledgeBaseService knowledgeBaseService(KnowledgeBaseRepository knowledgeBaseRepository,
                                              QuizSessionStore quizSessionStore) {
        return new KnowledgeBaseService(knowledgeBaseRepository, quizSessionStore);
    }

    @Bean
    ExternalIdentityService externalIdentityService(ExternalIdentityRepository externalIdentityRepository,
                                                    KnowledgeBaseRepository knowledgeBaseRepository,
                                                    TransactionOperations transactionOperations) {
        return new ExternalIdentityService(externalIdentityRepository, knowledgeBaseRepository, transactionOperations);
    }
}
