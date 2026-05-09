package ym.ymchat.service.color;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.ymchat.YmChatPlugin;

public final class PlayerColorPreferenceStore implements PlayerColorPreferenceRepository {

    private final File file;
    private final YamlConfiguration configuration;

    public PlayerColorPreferenceStore(YmChatPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "player-colors.yml");
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized PlayerColorPreference get(UUID playerId) {
        String path = path(playerId);
        String mode = configuration.getString(path + ".mode", "");
        String value = configuration.getString(path + ".value", "");
        if (mode.isBlank()) {
            return null;
        }
        return new PlayerColorPreference(PlayerColorPreference.Mode.parse(mode), value);
    }

    @Override
    public synchronized void save(UUID playerId, PlayerColorPreference preference) {
        String path = path(playerId);
        configuration.set(path + ".mode", preference.mode().name().toLowerCase());
        configuration.set(path + ".value", preference.value());
        flush();
    }

    @Override
    public synchronized void remove(UUID playerId) {
        configuration.set(path(playerId), null);
        flush();
    }

    private String path(UUID playerId) {
        return "players." + playerId;
    }

    private void flush() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save player color preferences", exception);
        }
    }
}
