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
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatConfigFileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsCompactRootConfigWithoutDefaultSplitFiles() throws Exception {
        YamlConfiguration root = resourceYaml("config.yml");
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertFalse(loaded.isConfigurationSection("Files"));
        assertFalse(loaded.isList("Formats"));
        assertEquals("global", loaded.getString("Channels.Default"));
        assertEquals(4, loaded.getMapList("Channels.List").size());
        assertEquals(3, loaded.getMapList("Channels.Formats").size());
        assertEquals("survival-1", loaded.getString("Cross-Server.Server-Id"));
        assertEquals("megaphones.yml", loaded.getString("Megaphone.Data-File"));
        assertEquals(1, loaded.getInt("Megaphone.Modes.Chat.Cost"));
        assertEquals("default", loadedConfig.formats().getFirst().id());
        assertTrue(loadedConfig.privateMessageSettings().enabled());
        assertTrue(loadedConfig.itemShowcaseSettings().enabled());
        assertEquals("ymchat.color.rgb.pink", loadedConfig.colorChatSettings().fixedSettings().findRgbColor("pink").permission());
        assertEquals("ymchat.namecolor.rgb.pink", loadedConfig.nameColorSettings().findRgbColor("pink").permission());
        assertEquals(1, loadedConfig.megaphoneSettings().mode(ym.ymchat.config.megaphone.MegaphoneMode.CHAT).cost());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void readsExistingLegacySidecarFilesWhenRootConfigOmitsSections() throws Exception {
        YamlConfiguration root = resourceYaml("config.yml");
        root.set("Channels", null);
        root.set("Formats", null);
        root.set("Private-Messages", null);
        Files.writeString(tempDir.resolve("formats.yml"), """
            Formats:
              - id: legacy-default
                channel: global
                priority: 100
                msg:
                  default-color: '&f'
                name:
                  - text: '&a%player_name%'
                message:
                  variants:
                    - text: '&f: {message}'
            """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("features.yml"), """
            Private-Messages:
              Enabled: true
              Format:
                Sender: '&d[PM -> %target_name%] &f{message}'
            """, StandardCharsets.UTF_8);
        Path channels = tempDir.resolve("channels");
        Files.createDirectories(channels);
        Files.writeString(channels.resolve("settings.yml"), """
            Show-Display: false
            Default: global
            """, StandardCharsets.UTF_8);
        Files.writeString(channels.resolve("global.yml"), """
            id: global
            display: '&8[&bGlobal&8] '
            target: ALL
            format: legacy-default
            cross-server: true
            """, StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertFalse(loaded.isConfigurationSection("Files"));
        assertEquals("global", loaded.getString("Channels.Default"));
        assertEquals(1, loaded.getMapList("Channels.List").size());
        assertEquals("survival-1", loaded.getString("Cross-Server.Server-Id"));
        assertEquals("legacy-default", loadedConfig.formats().getFirst().id());
        assertTrue(loadedConfig.privateMessageSettings().enabled());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void loadsBundledConfigDefaultsWhenSectionsAreMissing() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: zh_cn
            """);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertEquals("global", loaded.getString("Channels.Default"));
        assertEquals(4, loaded.getMapList("Channels.List").size());
        assertEquals(3, loaded.getMapList("Channels.Formats").size());
        assertEquals("staff", loaded.getMapList("Channels.List").get(3).get("id"));
        assertEquals("megaphones.yml", loaded.getString("Megaphone.Data-File"));
        assertEquals("&#FFFFFF: &#E0E0E0{message}", loadedConfig.formats().getFirst().messageVariants().get(3).text());
        assertEquals("#FF55FF", loadedConfig.colorChatSettings().fixedSettings().findRgbColor("pink").value());
        assertEquals("#FF55FF", loadedConfig.nameColorSettings().findRgbColor("pink").value());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void filesColorsPathFeedsChatNameAndSharedColorSections() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: en_us
            Files:
              Colors: 'custom-colors.yml'
            """);
        Files.writeString(tempDir.resolve("custom-colors.yml"), """
            Color-Chat:
              Inline:
                legacy-permission: 'custom.chat.inline.legacy'
              Fixed:
                enabled: true
            Name-Color:
              Fixed:
                enabled: true
            Colors:
              rgb-colors:
                - id: mint
                  display: '&aMint'
                  value: '#55FFAA'
                  chat-permission: 'custom.chat.mint'
                  name-permission: 'custom.name.mint'
            """, StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertEquals("custom.chat.inline.legacy", loadedConfig.colorChatSettings().inlineSettings().legacyPermission());
        assertEquals("custom.chat.mint", loadedConfig.colorChatSettings().fixedSettings().findRgbColor("mint").permission());
        assertEquals("custom.name.mint", loadedConfig.nameColorSettings().findRgbColor("mint").permission());
        assertEquals("#55FFAA", loadedConfig.nameColorSettings().findRgbColor("mint").value());
        assertTrue(warnings.stream().noneMatch(message -> message.contains("custom-colors.yml")), warnings::toString);
    }

    @Test
    void loadsBundledHighlightsFromStandaloneResourceByDefault() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: zh_cn
            """);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertTrue(loadedConfig.publicChatHighlightSettings().enabled());
        assertEquals("market-buy", loadedConfig.publicChatHighlightSettings().keywordRules().getFirst().id());
        assertFalse(loadedConfig.publicChatHighlightSettings().keywordRules().getFirst().hover().isEmpty());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void keepsLegacyRulesHighlightsWhenStandaloneFileIsStillBundledDefault() throws Exception {
        Files.writeString(tempDir.resolve("highlights.yml"),
            Files.readString(Path.of("src/main/resources/highlights.yml"), StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("rules.yml"), """
            Highlights:
              Enabled: false
              Keyword-Rules:
                - id: legacy-highlight
                  enabled: true
                  priority: 100
                  channels: ['*']
                  type: literal
                  match: 'legacy'
                  color: '&#123456'
            Anti-Spam:
              Enabled: true
            """, StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(resourceYaml("config.yml"));
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertFalse(loadedConfig.publicChatHighlightSettings().enabled());
        assertEquals("legacy-highlight", loadedConfig.publicChatHighlightSettings().keywordRules().getFirst().id());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void rootMegaphoneConfigOverridesBundledChannelMegaphoneDefaults() throws Exception {
        YamlConfiguration root = resourceYaml("config.yml");
        root.set("Megaphone.Data-File", "custom-horns.yml");
        root.set("Megaphone.Modes.Chat.Cost", 9);
        List<String> warnings = new ArrayList<>();

        FileConfiguration loaded = new ChatConfigFileLoader(tempDir.toFile(), this::resource, warnings::add).load(root);
        ChatPluginConfig loadedConfig = new ChatConfigLoader().load(loaded);

        assertEquals("custom-horns.yml", loaded.getString("Megaphone.Data-File"));
        assertEquals(9, loaded.getInt("Megaphone.Modes.Chat.Cost"));
        assertEquals("custom-horns.yml", loadedConfig.megaphoneSettings().dataFile());
        assertEquals(9, loadedConfig.megaphoneSettings().mode(ym.ymchat.config.megaphone.MegaphoneMode.CHAT).cost());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void mergesSplitConfigFilesIntoSingleView() throws Exception {
        YamlConfiguration root = yaml("""
            Options:
              Language: en_us
              Target: ALL
            Files:
              Channels: 'config/channels.yml'
              Colors: 'config/colors.yml'
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
            "Colors", yaml("""
                Color-Chat:
                  Fixed:
                    enabled: true
                Name-Color:
                  Fixed:
                    enabled: true
                Colors:
                  rgb-colors:
                    - id: mint
                      display: '&aMint'
                      value: '#55FFAA'
                      chat-permission: 'merged.chat.mint'
                      name-permission: 'merged.name.mint'
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
        assertEquals("merged.chat.mint", loaded.colorChatSettings().fixedSettings().findRgbColor("mint").permission());
        assertEquals("merged.name.mint", loaded.nameColorSettings().findRgbColor("mint").permission());
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

}
