package dev.mindforge.infrastructure.event;

import org.springframework.scheduling.annotation.Scheduled;

import dev.mindforge.application.service.ReminderService;

/** Sends the daily due-Flashcard reminders. */
public class DueReminderTrigger {

    private final ReminderService reminders;

    public DueReminderTrigger(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${mindforge.discord.reminder-cron:0 0 9 * * *}", zone = "${mindforge.discord.reminder-zone:UTC}")
    public void sendDueReminders() {
        reminders.sendDueReminders();
    }
}
