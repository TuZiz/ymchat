package ym.ymchat.config.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public enum TargetMode {
    ALL {
        @Override
        public List<Player> selectRecipients(Player sender) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }
    },
    WORLD {
        @Override
        public List<Player> selectRecipients(Player sender) {
            return new ArrayList<>(sender.getWorld().getPlayers());
        }
    };

    public abstract List<Player> selectRecipients(Player sender);

    public static TargetMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return TargetMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }
}
