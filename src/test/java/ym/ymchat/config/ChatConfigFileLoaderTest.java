package ym.ymchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatConfigFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRootConfigAndChannelFolderFromFilesSection() throws Exception {
        YamlConfiguration root = resourceYaml("config.yml");
        copyDefaultChannels(tempDir);
        copyDefaultSplitFiles(tempDir);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);

        assertFalse(loaded.isConfigurationSection("Files"));
        assertEquals("global", loaded.getString("Channels.Default"));
        assertEquals(3, loaded.getMapList("Channels.List").size());
        assertEquals("survival-1", loaded.getString("Cross-Server.Server-Id"));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void loadsBundledChannelDefaultsWhenChannelFolderIsMissing() throws Exception {
        YamlConfiguration root = resourceYaml("config.yml");
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertEquals("global", loaded.getString("Channels.Default"));
        assertEquals(3, loaded.getMapList("Channels.List").size());
        assertEquals("staff", loaded.getMapList("Channels.List").get(2).get("id"));
        assertEquals("&#FFFFFF: &#E0E0E0{message}", loadedConfig.formats().getFirst().messageVariants().get(3).text());
        assertTrue(warnings.stream().anyMatch(message -> message.contains("channels")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("formats.yml")));
    }

    @Test
    void mergesSplitConfigFilesIntoSingleView() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: en_us
              Target: ALL
            Files:
              Channels: 'config/channels.yml'
              Formats: 'config/formats.yml'
              Private-Messages: 'config/private-messages.yml'
              Highlights: 'config/highlights.yml'
              Item-Showcase: 'config/item-showcase.yml'
              Anti-Spam: 'config/anti-spam.yml'
              Filter: 'config/filter.yml'
            """);

        FileConfiguration merged = new ChatConfigFileLoader(null).merge(root, Map.of(
            "Channels", yaml("""
                Channels:
                  Default: global
                  List:
                    - id: global
                      display: '&8[&bGlobal&8] '
                      target: ALL
                      format: default
                      cross-server: true
                """),
            "Formats", yaml("""
                Formats:
                  - id: default
                    channel: global
                    priority: 100
                    msg:
                      default-color: '&f'
                    name:
                      - text: '&a%player_name%'
                    message:
                      variants:
                        - text: '&f: {message}'
                """),
            "Private-Messages", yaml("""
                Private-Messages:
                  Format:
                    Sender: '&d[PM -> %target_name%] &f{message}'
                    Receiver: '&d[%player_name% -> You] &f{message}'
                    Spy: '&8[Spy] &d%player_name% -> %target_name%: &f{message}'
                """),
            "Highlights", yaml("""
                Highlights:
                  Enabled: true
                  Keyword-Rules:
                    - id: buy
                      enabled: true
                      priority: 100
                      channels: ['*']
                      type: literal
                      match: 'buy'
                      color: '&#FFD166'
                """),
            "Item-Showcase", yaml("""
                Item-Showcase:
                  Enabled: true
                  Command-Message: 'show [i]'
                  Item:
                    Tokens: ['[i]']
                    Item-Text: '&eITEM'
                """),
            "Anti-Spam", yaml("""
                Anti-Spam:
                  Messages:
                    Too-Fast: '&cToo fast'
                    Too-Long: '&cToo long'
                    Too-Many-Caps: '&cToo many caps'
                    Duplicate: '&cDuplicate'
                """),
            "Filter", yaml("""
                Filter:
                  Enabled: true
                  Rules:
                    - id: link
                      scope: public
                      mode: replace
                      match: 'http'
                      replacement: '[link]'
                """)
        ));

        ChatPluginConfig loaded = new ChatConfigLoader().load(merged);

        assertEquals("&8[&bGlobal&8] ", loaded.defaultChannel().display());
        assertEquals("&d[PM -> %target_name%] &f{message}", loaded.privateMessageSettings().senderFormat());
        assertEquals("&cToo fast", loaded.antiSpamSettings().tooFastMessage());
        assertEquals("[link]", loaded.filterSettings().rules().getFirst().replacement());
        assertEquals("buy", loaded.publicChatHighlightSettings().keywordRules().getFirst().id());
        assertEquals("show [i]", loaded.itemShowcaseSettings().commandMessage());
        assertEquals("&eITEM", loaded.itemShowcaseSettings().itemText());
    }

    @Test
    void legacySplitLoadFallsBackToBundledSplitDefaultsWhenFilesAreMissing() throws Exception {
        List<String> warnings = new ArrayList<>();
        YamlConfiguration root = yaml("""
            Options:
              Language: en_us
            Files:
              Channels: 'config/missing-channels.yml'
            """);

        FileConfiguration merged = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);

        assertEquals("global", merged.getString("Channels.Default"));
        assertEquals("survival-1", merged.getString("Cross-Server.Server-Id"));
        assertFalse(Files.exists(tempDir.resolve("config")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("legacy config/")));
        assertTrue(warnings.stream().anyMatch(message -> message.contains("config/missing-channels.yml")));
    }

    @Test
    void keepsLegacySingleFileConfigWhenFilesSectionIsMissing() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: en_us
            Channels:
              Default: global
            """);

        FileConfiguration merged = new ChatConfigFileLoader(null).merge(root, Map.of());

        assertSame(root, merged);
        assertEquals("global", merged.getString("Channels.Default"));
    }

    private YamlConfiguration yaml(String source) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        return configuration;
    }

    private YamlConfiguration resourceYaml(String path) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(Files.readString(Path.of("src/main/resources").resolve(path), StandardCharsets.UTF_8));
        return configuration;
    }

    private InputStream resource(String path) {
        try {
            return Files.newInputStream(Path.of("src/main/resources").resolve(path));
        } catch (IOException exception) {
            return null;
        }
    }

    private void copyDefaultChannels(Path target) throws IOException {
        Path channels = target.resolve("channels");
        Files.createDirectories(channels);
        try (Stream<Path> stream = Files.walk(Path.of("src/main/resources/channels"))) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                Files.copy(file, channels.resolve(file.getFileName()));
            }
        }
    }

    private void copyDefaultSplitFiles(Path target) throws IOException {
        for (String name : List.of("formats.yml", "rules.yml", "features.yml")) {
            Files.copy(Path.of("src/main/resources").resolve(name), target.resolve(name));
        }
    }
}