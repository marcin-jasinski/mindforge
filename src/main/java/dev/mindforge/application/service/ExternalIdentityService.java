package dev.mindforge.application.service;

import java.util.UUID;

import org.springframework.transaction.support.TransactionOperations;

import dev.mindforge.domain.model.ExternalAccounts;
import dev.mindforge.domain.model.ExternalIdentityConflictException;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.TextRules;
import dev.mindforge.domain.model.User;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.KnowledgeBaseRepository;

/**
 * Turns a chat-platform user into a MindForge user (ADR 0027, ADR 0030). On first contact it provisions the account,
 * its identity link and one knowledge base named after the platform, in one transaction.
 */
public class ExternalIdentityService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 255;

    private final ExternalIdentityRepository identities;
    private final KnowledgeBaseRepository knowledgeBases;
    private final TransactionOperations transactions;

    public ExternalIdentityService(ExternalIdentityRepository identities, KnowledgeBaseRepository knowledgeBases,
                                   TransactionOperations transactions) {
        this.identities = identities;
        this.knowledgeBases = knowledgeBases;
        this.transactions = transactions;
    }

    /**
     * Returns the user linked to the identity, provisioning one on first contact. When a concurrent first contact wins
     * the insert, returns the winner's user and creates nothing.
     *
     * @throws ExternalIdentityConflictException when the insert fails and no user is linked to the identity
     */
    public UUID resolve(ExternalPlatform platform, String externalId, String displayName) {
        return identities.findUserId(platform, externalId)
            .orElseGet(() -> provision(platform, externalId, displayName));
    }

    private UUID provision(ExternalPlatform platform, String externalId, String displayName) {
        User user = new User(UUID.randomUUID(), cleanDisplayName(displayName, externalId),
            ExternalAccounts.syntheticEmail(platform, externalId), null, null, null, null);
        try {
            return transactions.execute(status -> {
                User inserted = identities.createUserAndLink(platform, externalId, user);
                knowledgeBases.insert(new KnowledgeBase(UUID.randomUUID(), inserted.userId(), platform.label(), null,
                    null, 0, 0));
                return inserted.userId();
            });
        } catch (ExternalIdentityConflictException conflict) {
            return identities.findUserId(platform, externalId).orElseThrow(() -> conflict);
        }
    }

    private static String cleanDisplayName(String displayName, String externalId) {
        String name = displayName == null ? "" : TextRules.singleLine(displayName);
        if (name.isEmpty()) {
            name = externalId;
        }
        if (name.length() <= MAX_DISPLAY_NAME_LENGTH) {
            return name;
        }
        int end = Character.isHighSurrogate(name.charAt(MAX_DISPLAY_NAME_LENGTH - 1))
            ? MAX_DISPLAY_NAME_LENGTH - 1 : MAX_DISPLAY_NAME_LENGTH;
        return name.substring(0, end);
    }
}
