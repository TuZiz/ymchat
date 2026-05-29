package ym.ymchat.service.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerColorPreferenceStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void readsLegacyPlayerColorStructureAsChatScope() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path file = tempDir.resolve("player-colors.yml");
        Files.writeString(
            file,
            "players:\n"
                + "  " + playerId + ":\n"
                + "    mode: legacy\n"
                + "    value: d\n",
            StandardCharsets.UTF_8
        );

        PlayerColorPreferenceStore store = new PlayerColorPreferenceStore(file.toFile());

        assertEquals(PlayerColorPreference.Mode.LEGACY, store.get(playerId, ColorScope.CHAT).mode());
        assertEquals("d", store.get(playerId, ColorScope.CHAT).value());
        assertNull(store.get(playerId, ColorScope.NAME));
    }

    @Test
    void savesChatAndNameScopesIndependentlyInNewStructure() {
        UUID playerId = UUID.randomUUID();
        Path file = tempDir.resolve("player-colors.yml");
        PlayerColorPreferenceStore store = new PlayerColorPreferenceStore(file.toFile());

        store.save(playerId, ColorScope.CHAT, PlayerColorPreference.legacy("a"));
        store.save(playerId, ColorScope.NAME, PlayerColorPreference.rgb("pink"));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("legacy", saved.getString("players." + playerId + ".chat.mode"));
        assertEquals("a", saved.getString("players." + playerId + ".chat.value"));
        assertEquals("rgb", saved.getString("players." + playerId + ".name.mode"));
        assertEquals("pink", saved.getString("players." + playerId + ".name.value"));
        assertNull(saved.getString("players." + playerId + ".mode"));
    }
}
