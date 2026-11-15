package dev.mindforge.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mindforge.domain.model.ExternalIdentity;
import dev.mindforge.domain.model.ExternalPlatform;
import dev.mindforge.domain.model.KnowledgeBase;
import dev.mindforge.domain.model.StudyScope;
import dev.mindforge.domain.port.ExternalIdentityRepository;
import dev.mindforge.domain.port.ReminderSender;

/**
 * Tells each linked Discord user, in one direct message, how many Flashcards are due in each knowledge base they own.
 * Only counts and knowledge base names leave MindForge; nothing is generated, and a user with nothing due gets no
 * message. A failure for one user is logged and the next user is still reminded.
 */
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    static final int MAX_MESSAGE_LENGTH = 2000;

    private final ExternalIdentityRepository identities;
    private final KnowledgeBaseService knowledgeBases;
    private final FlashcardService flashcards;
    private final ReminderSender sender;

    public ReminderService(ExternalIdentityRepository identities, KnowledgeBaseService knowledgeBases,
                           FlashcardService flashcards, ReminderSender sender) {
        this.identities = identities;
        this.knowledgeBases = knowledgeBases;
        this.flashcards = flashcards;
        this.sender = sender;
    }

    public void sendDueReminders() {
        for (ExternalIdentity identity : identities.findByPlatform(ExternalPlatform.DISCORD)) {
            try {
                List<String> lines = dueLines(identity);
                if (!lines.isEmpty()) {
                    sender.send(identity.externalId(), message(lines));
                }
            } catch (RuntimeException e) {
                log.warn("Reminder DM failed for user {}: {}", identity.userId(), e.getClass().getSimpleName());
            }
        }
    }

    private List<String> dueLines(ExternalIdentity identity) {
        List<String> lines = new ArrayList<>();
        knowledgeBases.list(identity.userId()).stream()
            .sorted(Comparator.comparing(KnowledgeBase::name, String.CASE_INSENSITIVE_ORDER))
            .forEach(knowledgeBase -> {
                int due = flashcards.due(knowledgeBase.kbId(), new StudyScope.WholeKnowledgeBase()).size();
                if (due > 0) {
                    lines.add("You have " + due + " flashcards due in *" + knowledgeBase.name() + "*");
                }
            });
        return lines;
    }

    /** The lines, one per row; when they exceed the limit, as many as fit followed by "…and N more". */
    private static String message(List<String> lines) {
        String all = String.join("\n", lines);
        if (all.length() <= MAX_MESSAGE_LENGTH) {
            return all;
        }
        StringBuilder text = new StringBuilder();
        int kept = 0;
        for (String line : lines) {
            int length = text.length() + line.length() + 1 + more(lines.size() - kept - 1).length();
            if (length > MAX_MESSAGE_LENGTH) {
                break;
            }
            text.append(line).append('\n');
            kept++;
        }
        return text.append(more(lines.size() - kept)).toString();
    }

    private static String more(int count) {
        return "…and " + count + " more";
    }
}
