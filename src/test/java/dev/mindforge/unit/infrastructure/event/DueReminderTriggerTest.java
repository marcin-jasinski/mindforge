package dev.mindforge.unit.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import dev.mindforge.application.service.ReminderService;
import dev.mindforge.infrastructure.event.DueReminderTrigger;

class DueReminderTriggerTest {

    // AC7

    @Test
    void sendDueReminders_delegatesOnce() {
        ReminderService reminders = mock(ReminderService.class);

        new DueReminderTrigger(reminders).sendDueReminders();

        verify(reminders).sendDueReminders();
        verifyNoMoreInteractions(reminders);
    }

    @Test
    void sendDueReminders_runsOnTheConfiguredDailyCronAndZone() throws NoSuchMethodException {
        Scheduled scheduled = DueReminderTrigger.class.getMethod("sendDueReminders").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${mindforge.discord.reminder-cron:0 0 9 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${mindforge.discord.reminder-zone:UTC}");
    }
}
