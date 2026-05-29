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
        this(new File(plugin.getDataFolder(), "player-colors.yml"));
    }

    PlayerColorPreferenceStore(File file) {
        this.file = file;
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized PlayerColorPreference get(UUID playerId, ColorScope scope) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        String path = path(playerId, effectiveScope);
        String mode = configuration.getString(path + ".mode", "");
        String value = configuration.getString(path + ".value", "");
        if (mode.isBlank() && effectiveScope == ColorScope.CHAT) {
            String legacyPath = legacyPath(playerId);
            mode = configuration.getString(legacyPath + ".mode", "");
            value = configuration.getString(legacyPath + ".value", "");
        }
        if (mode.isBlank()) {
            return null;
        }
        return new PlayerColorPreference(PlayerColorPreference.Mode.parse(mode), value);
    }

    @Override
    public synchronized void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        String path = path(playerId, effectiveScope);
        configuration.set(path + ".mode", preference.mode().name().toLowerCase());
        configuration.set(path + ".value", preference.value());
        if (effectiveScope == ColorScope.CHAT) {
            clearLegacyChatPath(playerId);
        }
        flush();
    }

    @Override
    public synchronized void remove(UUID playerId, ColorScope scope) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        configuration.set(path(playerId, effectiveScope), null);
        if (effectiveScope == ColorScope.CHAT) {
            clearLegacyChatPath(playerId);
        }
        flush();
    }

    private String legacyPath(UUID playerId) {
        return "players." + playerId;
    }

    private String path(UUID playerId, ColorScope scope) {
        return legacyPath(playerId) + "." + scope.storageKey();
    }

    private void clearLegacyChatPath(UUID playerId) {
        String legacyPath = legacyPath(playerId);
        configuration.set(legacyPath + ".mode", null);
        configuration.set(legacyPath + ".value", null);
    }

    private void flush() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save player color preferences", exception);
        }
    }
}
