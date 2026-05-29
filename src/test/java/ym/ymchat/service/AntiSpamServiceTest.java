package ym.ymchat.service.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.chat.AntiSpamSettings;

class AntiSpamServiceTest {

    @Test
    void allowsShortUppercaseMessages() {
        AntiSpamService service = new AntiSpamService();

        AntiSpamService.CheckResult result = service.check(
            UUID.randomUUID(),
            false,
            "OK ABC",
            settings(8)
        );

        assertTrue(result.allowed());
    }

    @Test
    void allowsLongUppercaseMessages() {
        AntiSpamService service = new AntiSpamService();

        AntiSpamService.CheckResult result = service.check(
            UUID.randomUUID(),
            false,
            "THIS IS TOO LOUD",
            settings(0D, 8)
        );

        assertTrue(result.allowed());
    }

    @Test
    void allowsLongUppercaseMessagesEvenWhenLegacyCapsRatioIsConfigured() {
        AntiSpamService service = new AntiSpamService();

        AntiSpamService.CheckResult result = service.check(
            UUID.randomUUID(),
            false,
            "THIS IS TOO LOUD",
            settings(0.7D, 8)
        );

        assertTrue(result.allowed());
    }

    @Test
    void allowsDuplicateMessagesEvenWhenLegacyDuplicateBlockingIsConfigured() {
        AntiSpamService service = new AntiSpamService();
        UUID playerId = UUID.randomUUID();
        AntiSpamSettings settings = new AntiSpamSettings(
            true,
            "ymchat.bypass.antispam",
            0L,
            120,
            0D,
            8,
            30000L,
            true,
            "too fast",
            "too long",
            "too many caps",
            "duplicate"
        );

        assertTrue(service.check(playerId, false, "same message", settings).allowed());
        assertTrue(service.check(playerId, false, "same message", settings).allowed());
    }

    private AntiSpamSettings settings(int capsMinLetters) {
        return settings(0.7D, capsMinLetters);
    }

    private AntiSpamSettings settings(double capsRatio, int capsMinLetters) {
        return new AntiSpamSettings(
            true,
            "ymchat.bypass.antispam",
            0L,
            120,
            capsRatio,
            capsMinLetters,
            0L,
            false,
            "too fast",
            "too long",
            "too many caps",
            "duplicate"
        );
    }
}
