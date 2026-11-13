package dev.mindforge.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Ends the CLI process after every runner has returned: closes the context through {@link SpringApplication#exit}
 * and ends the JVM with the exit code the runners report. Without it the non-web context keeps the JVM alive.
 */
public class CliExitOnReady implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        System.exit(SpringApplication.exit(event.getApplicationContext()));
    }
}
