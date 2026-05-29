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
        return check(player.getUniqueId(), player.hasPermission(settings.bypassPermission()), rawMessage, settings);
    }

    CheckResult check(UUID playerId, boolean bypass, String rawMessage, AntiSpamSettings settings) {
        if (!settings.enabled() || bypass) {
            return CheckResult.pass();
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        if (settings.maxLength() > 0 && message.length() > settings.maxLength()) {
            return CheckResult.blocked(settings.tooLongMessage(), "max-length");
        }

        long now = System.currentTimeMillis();
        SpamState previous = states.get(playerId);
        if (previous != null) {
            if (settings.cooldownMillis() > 0 && now - previous.lastMessageAt() < settings.cooldownMillis()) {
                return CheckResult.blocked(settings.tooFastMessage(), "cooldown");
            }
        }

        states.put(playerId, new SpamState(now, normalize(message)));
        return CheckResult.pass();
    }

    private String normalize(String message) {
        return message.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
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
