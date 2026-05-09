package ym.ymchat.service.text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {

    private static final Pattern SERVER_TIME = Pattern.compile("%server_time_([^%]+)%");

    private PlaceholderResolver() {
    }

    public static String resolve(Player player, String input) {
        if (input == null || input.isBlank()) {
            return input == null ? "" : input;
        }

        String resolved = input
            .replace("%player_name%", player.getName())
            .replace("%player_displayname%", player.getDisplayName())
            .replace("%player_ping%", Integer.toString(player.getPing()))
            .replace("%player_health_rounded%", Integer.toString((int) Math.round(player.getHealth())))
            .replace("%luckperms_prefix%", resolveLuckPermsPrefix(player));

        resolved = replaceServerTime(resolved);
        return applyPlaceholderApi(player, resolved);
    }

    private static String replaceServerTime(String input) {
        Matcher matcher = SERVER_TIME.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String pattern = matcher.group(1);
            String formatted;
            try {
                formatted = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.US));
            } catch (IllegalArgumentException exception) {
                formatted = matcher.group(0);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String resolveLuckPermsPrefix(Player player) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) {
                return "";
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            return prefix == null ? "" : prefix.toString();
        } catch (ReflectiveOperationException | IllegalStateException ignored) {
            return "";
        }
    }

    private static String applyPlaceholderApi(Player player, String input) {
        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            String resolved = input;
            if (containsPlaceholders(placeholderApiClass, "containsPlaceholders", resolved)) {
                resolved = invokePlaceholderMethod(placeholderApiClass, "setPlaceholders", player, resolved);
            }
            if (containsPlaceholders(placeholderApiClass, "containsBracketPlaceholders", resolved)) {
                resolved = invokePlaceholderMethod(placeholderApiClass, "setBracketPlaceholders", player, resolved);
            }
            return resolved;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return input;
        }
    }

    private static boolean containsPlaceholders(Class<?> placeholderApiClass, String methodName, String input)
        throws ReflectiveOperationException {
        Object output = placeholderApiClass
            .getMethod(methodName, String.class)
            .invoke(null, input);
        return output instanceof Boolean flag && flag;
    }

    private static String invokePlaceholderMethod(Class<?> placeholderApiClass, String methodName, Player player, String input)
        throws ReflectiveOperationException {
        Object output = placeholderApiClass
            .getMethod(methodName, OfflinePlayer.class, String.class)
            .invoke(null, player, input);
        return output instanceof String string ? string : input;
    }
}
