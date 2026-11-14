package dev.mindforge.discord;

import java.time.Duration;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Waits at startup until the bot is logged in, and logs it out when the context closes. */
public class DiscordBot implements ApplicationRunner, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DiscordBot.class);

    private final JDA jda;
    private final Duration shutdownWait;

    public DiscordBot(JDA jda, Duration shutdownWait) {
        this.jda = jda;
        this.shutdownWait = shutdownWait;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        jda.awaitReady();
        log.info("Discord bot ready as {} in {} guilds", jda.getSelfUser().getName(), jda.getGuilds().size());
    }

    @Override
    public void close() throws InterruptedException {
        jda.shutdown();
        if (!jda.awaitShutdown(shutdownWait)) {
            jda.shutdownNow();
        }
    }
}
