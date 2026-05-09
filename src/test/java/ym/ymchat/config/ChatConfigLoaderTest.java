package ym.ymchat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ym.ymchat.config.megaphone.MegaphoneMode;
import ym.ymchat.service.language.LanguageService;

class ChatConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesLangReferencesWhenConfigUsesLanguageKeys() throws IOException {
        writeLocales("""
            common:
              greeting: 'Hello CN'
            channels:
              defaults:
                global:
                  display: '&8[&bGlobal CN&8] '
            anti-spam:
              messages:
                too-fast: '&cToo fast CN'
            color-chat:
              rgb:
                pink:
                  display: '&#FF55FFPink CN'
            """, """
            common:
              greeting: 'Hello'
            """);

        LanguageService languageService = new LanguageService(tempDir.toFile());
        YamlConfiguration mainConfig = new YamlConfiguration();
        mainConfig.set("Options.Language", "zh_cn");
        languageService.reload(mainConfig);

        YamlConfiguration config = new YamlConfiguration();
        config.set("Channels.Default", "global");
        config.set("Channels.List", List.of(java.util.Map.of(
            "id", "global",
            "display", "lang:channels.defaults.global.display",
            "target", "ALL",
            "format", "default",
            "cross-server", true
        )));
        config.set("Anti-Spam.Messages.Too-Fast", "lang:anti-spam.messages.too-fast");
        config.set("Color-Chat.Fixed.rgb-colors", List.of(java.util.Map.of(
            "id", "pink",
            "display", "lang:color-chat.rgb.pink.display",
            "permission", "ymchat.color.rgb.pink",
            "value", "#FF55FF"
        )));

        ChatPluginConfig loaded = new ChatConfigLoader(languageService).load(config);

        assertEquals("&8[&bGlobal CN&8] ", loaded.defaultChannel().display());
        assertEquals("&cToo fast CN", loaded.antiSpamSettings().tooFastMessage());
        assertEquals("&#FF55FFPink CN", loaded.colorChatSettings().fixedSettings().findRgbColor("pink").display());
    }

    @Test
    void loadsCrossServerLogSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Cross-Server.Enabled", true);
        config.set("Cross-Server.Server-Id", "survival-1");
        config.set("Cross-Server.Database.Host", "127.0.0.1");
        config.set("Cross-Server.Database.Database", "ymchat");
        config.set("Cross-Server.Database.Username", "postgres");
        config.set("Cross-Server.Database.Table", "ymchat_cross_messages");
        config.set("Cross-Server.Logs.Enabled", true);
        config.set("Cross-Server.Logs.Default-Since", "6h");
        config.set("Cross-Server.Logs.Default-Limit", 12);
        config.set("Cross-Server.Logs.Max-Limit", 25);
        config.set("Cross-Server.Logs.Line-Format", "&bLine %message%");

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals("6h", loaded.crossServerSettings().logs().defaultSince());
        assertEquals(12, loaded.crossServerSettings().logs().defaultLimit());
        assertEquals(25, loaded.crossServerSettings().logs().maxLimit());
        assertEquals("&bLine %message%", loaded.crossServerSettings().logs().lineFormat());
    }

    @Test
    void loadsPublicHighlightSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Highlights.Enabled", true);
        config.set("Highlights.Default-Channels", List.of("global", "trade"));
        config.set("Highlights.Keyword-Rules", List.of(java.util.Map.of(
            "id", "buy",
            "enabled", true,
            "priority", 120,
            "channels", List.of("global"),
            "type", "literal",
            "match", "buy",
            "color", "&#FFD166",
            "formats", List.of("bold")
        )));
        config.set("Highlights.Pattern-Rules.price.enabled", true);
        config.set("Highlights.Pattern-Rules.price.regexes", List.of("\\b\\d+\\b"));

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(List.of("global", "trade"), loaded.publicChatHighlightSettings().defaultChannels());
        assertEquals("buy", loaded.publicChatHighlightSettings().keywordRules().getFirst().id());
        assertEquals("buy", loaded.publicChatHighlightSettings().keywordRules().getFirst().match());
        assertEquals("price", loaded.publicChatHighlightSettings().patternRules().getFirst().id());
    }

    @Test
    void loadsNestedItemShowcaseSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Item-Showcase.Command-Message", "show [i]");
        config.set("Item-Showcase.Item.Tokens", List.of("[i]", ":i:"));
        config.set("Item-Showcase.Item.Item-Text", "&eITEM");
        config.set("Item-Showcase.Inventory.Tokens", List.of("[inv]"));
        config.set("Item-Showcase.Inventory.Text", "&b[Inventory]");
        config.set("Item-Showcase.Ender-Chest.Tokens", List.of("[ec]"));
        config.set("Item-Showcase.Position.Tokens", List.of("[pos]"));

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals("show [i]", loaded.itemShowcaseSettings().commandMessage());
        assertEquals("&eITEM", loaded.itemShowcaseSettings().itemText());
        assertEquals(List.of("[inv]"), loaded.itemShowcaseSettings().inventory().tokens());
        assertEquals("&b[Inventory]", loaded.itemShowcaseSettings().inventory().text());
        assertEquals(List.of("[ec]"), loaded.itemShowcaseSettings().enderChest().tokens());
        assertEquals(List.of("[pos]"), loaded.itemShowcaseSettings().position().tokens());
    }

    @Test
    void loadsMegaphoneSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Megaphone.Data-File", "horns.yml");
        config.set("Megaphone.Default-Mode", "title");
        config.set("Megaphone.Modes.Chat.Cost", 2);
        config.set("Megaphone.Modes.Title.Cost", 4);
        config.set("Megaphone.Modes.BossBar.Cost", 6);

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals("horns.yml", loaded.megaphoneSettings().dataFile());
        assertEquals(MegaphoneMode.TITLE, loaded.megaphoneSettings().defaultMode());
        assertEquals(2, loaded.megaphoneSettings().mode(MegaphoneMode.CHAT).cost());
        assertEquals(4, loaded.megaphoneSettings().mode(MegaphoneMode.TITLE).cost());
        assertEquals(6, loaded.megaphoneSettings().mode(MegaphoneMode.BOSSBAR).cost());
    }

    private void writeLocales(String zhCn, String enUs) throws IOException {
        Path langDir = tempDir.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("zh_cn.yml"), zhCn, StandardCharsets.UTF_8);
        Files.writeString(langDir.resolve("en_us.yml"), enUs, StandardCharsets.UTF_8);
    }
}
