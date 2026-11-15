package dev.mindforge.discord;

import java.time.Duration;
import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Attaches the listener, waits at startup until the bot is logged in, registers the commands (guild context only),
 * and logs the bot out when the context closes.
 */
public class DiscordBot implements ApplicationRunner, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DiscordBot.class);

    private final JDA jda;
    private final DiscordListener listener;
    private final List<DiscordCommand> commands;
    private final Duration shutdownWait;

    public DiscordBot(JDA jda, DiscordListener listener, List<DiscordCommand> commands, Duration shutdownWait) {
        this.jda = jda;
        this.listener = listener;
        this.commands = List.copyOf(commands);
        this.shutdownWait = shutdownWait;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        jda.addEventListener(listener);
        jda.awaitReady();
        jda.updateCommands()
            .addCommands(commands.stream()
                .map(command -> command.definition().setContexts(InteractionContextType.GUILD))
                .toList())
            .complete();
        log.info("Discord bot ready as {} in {} guilds with {} commands", jda.getSelfUser().getName(),
            jda.getGuilds().size(), commands.size());
    }

    @Override
    public void close() throws InterruptedException {
        jda.shutdown();
        if (!jda.awaitShutdown(shutdownWait)) {
            jda.shutdownNow();
        }
    }
}
