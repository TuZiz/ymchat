package me.clip.placeholderapi;

import java.util.List;
import org.bukkit.OfflinePlayer;

public final class PlaceholderAPI {

    private PlaceholderAPI() {
    }

    public static boolean containsPlaceholders(String input) {
        return input != null && input.contains("%");
    }

    public static boolean containsBracketPlaceholders(String input) {
        return input != null && input.contains("{") && input.contains("}");
    }

    public static String setPlaceholders(OfflinePlayer player, String input) {
        if (input != null && input.contains("%explode%")) {
            throw new IllegalStateException("boom");
        }
        String name = player == null || player.getName() == null ? "unknown" : player.getName();
        return input == null ? "" : input
            .replace("%db_value%", "db-" + name)
            .replace("%db_number%", "42");
    }

    public static List<String> setPlaceholders(OfflinePlayer player, List<String> input) {
        return input;
    }

    public static String setBracketPlaceholders(OfflinePlayer player, String input) {
        String name = player == null || player.getName() == null ? "unknown" : player.getName();
        return input == null ? "" : input
            .replace("{db_value}", "bracket-" + name);
    }

    public static List<String> setBracketPlaceholders(OfflinePlayer player, List<String> input) {
        return input;
    }
}
