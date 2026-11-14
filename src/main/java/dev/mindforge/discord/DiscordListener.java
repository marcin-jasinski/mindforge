package dev.mindforge.discord;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** Translates JDA interaction events into {@link DiscordCall}s; holds no logic of its own. */
public class DiscordListener extends ListenerAdapter {

    private final DiscordCommandHandler handler;
    private final DiscordKnowledgeBases knowledgeBases;

    public DiscordListener(DiscordCommandHandler handler, DiscordKnowledgeBases knowledgeBases) {
        this.handler = handler;
        this.knowledgeBases = knowledgeBases;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        handler.handle(toCall(event), new JdaDiscordReply(event));
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!DiscordKnowledgeBases.OPTION.equals(event.getFocusedOption().getName())) {
            return;
        }
        event.replyChoices(knowledgeBases.suggest(toCall(event), event.getFocusedOption().getValue())).complete();
    }

    static <E extends Interaction & CommandInteractionPayload> DiscordCall toCall(E event) {
        Member member = event.getMember();
        Set<Long> roleIds = member == null ? Set.of()
            : member.getRoles().stream().map(ISnowflake::getIdLong).collect(Collectors.toUnmodifiableSet());
        User user = event.getUser();
        String displayName = user.getGlobalName() != null ? user.getGlobalName() : user.getName();
        Map<String, String> options = event.getOptions().stream()
            .collect(Collectors.toUnmodifiableMap(OptionMapping::getName, OptionMapping::getAsString));
        return new DiscordCall(event.getGuild() == null ? null : event.getGuild().getIdLong(), roleIds,
            user.getIdLong(), displayName, event.getName(), event.getSubcommandName(), options);
    }
}
