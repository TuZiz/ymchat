package ym.ymchat.service.chat;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import ym.ymchat.config.chat.AntiSpamSettings;

public final class AntiSpamService {

    private final Map<UUID, SpamState> states = new ConcurrentHashMap<>();

    public CheckResult check(Player player, String rawMessage, AntiSpamSettings settings) {
        if (!settings.enabled() || player.hasPermission(settings.bypassPermission())) {
            return CheckResult.pass();
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (settings.maxLength() > 0 && message.length() > settings.maxLength()) {
            return CheckResult.blocked(settings.tooLongMessage(), "max-length");
        }
        if (settings.capsRatio() > 0 && capsRatio(message) > settings.capsRatio()) {
            return CheckResult.blocked(settings.tooManyCapsMessage(), "caps-ratio");
        }

        long now = System.currentTimeMillis();
        SpamState previous = states.get(player.getUniqueId());
        if (previous != null) {
            if (settings.cooldownMillis() > 0 && now - previous.lastMessageAt() < settings.cooldownMillis()) {
                return CheckResult.blocked(settings.tooFastMessage(), "cooldown");
            }
            if (settings.blockDuplicate()
                && settings.duplicateWindowMillis() > 0
                && now - previous.lastMessageAt() < settings.duplicateWindowMillis()
                && normalize(message).equals(previous.normalizedMessage())) {
                return CheckResult.blocked(settings.duplicateMessage(), "duplicate");
            }
        }

        states.put(player.getUniqueId(), new SpamState(now, normalize(message)));
        return CheckResult.pass();
    }

    private String normalize(String message) {
        return message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private double capsRatio(String message) {
        int letters = 0;
        int upper = 0;
        for (char character : message.toCharArray()) {
            if (Character.isLetter(character)) {
                letters++;
                if (Character.isUpperCase(character)) {
                    upper++;
                }
            }
        }
        return letters == 0 ? 0D : (double) upper / letters;
    }

    private record SpamState(long lastMessageAt, String normalizedMessage) {
    }

    public record CheckResult(boolean allowed, String message, String reason) {

        public static CheckResult pass() {
            return new CheckResult(true, null, "allowed");
        }

        public static CheckResult blocked(String message, String reason) {
            return new CheckResult(false, message, reason);
        }
    }
}
