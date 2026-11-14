package dev.mindforge.unit.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.mindforge.discord.DiscordCall;
import dev.mindforge.discord.DiscordCommandHandler;
import dev.mindforge.discord.DiscordKnowledgeBases;
import dev.mindforge.discord.DiscordListener;
import dev.mindforge.discord.JdaDiscordReply;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;

class DiscordListenerTest {

    private final DiscordCommandHandler handler = mock(DiscordCommandHandler.class);
    private final DiscordKnowledgeBases knowledgeBases = mock(DiscordKnowledgeBases.class);
    private final DiscordListener listener = new DiscordListener(handler, knowledgeBases);

    // AC9

    @Test
    void aSlashCommand_isTranslatedIntoAJdaFreeCallAndAnsweredThroughTheReplySeam() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(100L);
        when(event.getGuild()).thenReturn(guild);
        Member member = mock(Member.class);
        Role role = mock(Role.class);
        when(role.getIdLong()).thenReturn(200L);
        when(member.getRoles()).thenReturn(List.of(role));
        when(event.getMember()).thenReturn(member);
        User user = makeUser(null, "ada_l");
        when(event.getUser()).thenReturn(user);
        when(event.getName()).thenReturn("quiz");
        when(event.getSubcommandName()).thenReturn("start");
        OptionMapping kb = mock(OptionMapping.class);
        when(kb.getName()).thenReturn("kb");
        when(kb.getAsString()).thenReturn("value");
        when(event.getOptions()).thenReturn(List.of(kb));

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<DiscordCall> call = ArgumentCaptor.forClass(DiscordCall.class);
        verify(handler).handle(call.capture(), any(JdaDiscordReply.class));
        assertThat(call.getValue()).isEqualTo(new DiscordCall(100L, Set.of(200L), 300L, "ada_l", "quiz",
            "start", Map.of("kb", "value")));
    }

    @Test
    void aDmCommand_hasNoGuildAndNoRoles_andPrefersTheGlobalName() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = makeUser("Ada", "ada_l");
        when(event.getUser()).thenReturn(user);
        when(event.getName()).thenReturn("ask");
        when(event.getOptions()).thenReturn(List.of());

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<DiscordCall> call = ArgumentCaptor.forClass(DiscordCall.class);
        verify(handler).handle(call.capture(), any(JdaDiscordReply.class));
        assertThat(call.getValue().guildId()).isNull();
        assertThat(call.getValue().memberRoleIds()).isEmpty();
        assertThat(call.getValue().displayName()).isEqualTo("Ada");
    }

    @Test
    void anAttachmentOption_isCarriedAsTheJdaAttachment() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = makeUser("Ada", "ada_l");
        when(event.getUser()).thenReturn(user);
        when(event.getName()).thenReturn("upload");
        OptionMapping file = mock(OptionMapping.class);
        Message.Attachment attachment = mock(Message.Attachment.class);
        when(file.getName()).thenReturn("file");
        when(file.getType()).thenReturn(OptionType.ATTACHMENT);
        when(file.getAsString()).thenReturn("1");
        when(file.getAsAttachment()).thenReturn(attachment);
        when(event.getOptions()).thenReturn(List.of(file));

        listener.onSlashCommandInteraction(event);

        ArgumentCaptor<DiscordCall> call = ArgumentCaptor.forClass(DiscordCall.class);
        verify(handler).handle(call.capture(), any(JdaDiscordReply.class));
        assertThat(call.getValue().attachments()).containsExactly(Map.entry("file", attachment));
    }

    @Test
    void kbAutocomplete_repliesWithTheSuggestedChoices() {
        CommandAutoCompleteInteractionEvent event = mock(CommandAutoCompleteInteractionEvent.class);
        AutoCompleteQuery focused = mock(AutoCompleteQuery.class);
        when(focused.getName()).thenReturn("kb");
        when(focused.getValue()).thenReturn("bio");
        when(event.getFocusedOption()).thenReturn(focused);
        User user = makeUser("Ada", "ada_l");
        when(event.getUser()).thenReturn(user);
        when(event.getName()).thenReturn("ask");
        when(event.getOptions()).thenReturn(List.of());
        List<Command.Choice> choices = List.of(new Command.Choice("Biology", "id"));
        when(knowledgeBases.suggest(any(), any())).thenReturn(choices);
        AutoCompleteCallbackAction action = mock(AutoCompleteCallbackAction.class);
        when(event.replyChoices(anyCollection())).thenReturn(action);

        listener.onCommandAutoCompleteInteraction(event);

        verify(knowledgeBases).suggest(any(DiscordCall.class), eq("bio"));
        verify(event).replyChoices(choices);
        verify(action).complete();
    }

    private static User makeUser(String globalName, String name) {
        User user = mock(User.class);
        when(user.getIdLong()).thenReturn(300L);
        when(user.getGlobalName()).thenReturn(globalName);
        when(user.getName()).thenReturn(name);
        return user;
    }
}
