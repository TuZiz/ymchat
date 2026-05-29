package ym.ymchat.config.chat;

import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;

public record ChatChannel(
    String id,
    String display,
    TargetMode targetMode,
    String permission,
    String format,
    boolean crossServer,
    List<String> aliases
) {

    public ChatChannel {
        aliases = aliases == null ? List.of() : aliases.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public boolean canUse(Player player) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public boolean matches(String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return id.equalsIgnoreCase(lowered) || aliases.contains(lowered);
    }

    public static ChatChannel global() {
        return new ChatChannel("global", "&8[&bGlobal&8] ", TargetMode.ALL, "", "default", false, List.of("g"));
    }
}
