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
                too-long: '&cToo long CN'
                too-many-caps: '&cToo many caps CN'
                duplicate: '&cDuplicate CN'
            filter:
              default-blocked-message: '&cBlocked CN'
            private-messages:
              messages:
                disabled: '私聊关闭'
                no-reply-target: '没有回复目标'
                offline: '玩家 %target_name% 离线'
                self: '不能给自己发私聊'
            item-showcase:
              command-message: '展示了 [物品]'
              messages:
                disabled: '展示关闭'
                only-player: '只有玩家可以展示'
              item:
                messages:
                  no-permission: '没有物品展示权限'
                  empty-hand: '主手没有物品'
                  cooldown: '物品展示冷却'
              inventory:
                messages:
                  no-permission: '没有背包展示权限'
                  empty: '没有背包内容'
                  cooldown: '背包展示冷却'
              ender-chest:
                messages:
                  no-permission: '没有末影箱展示权限'
                  empty: '没有末影箱内容'
                  cooldown: '末影箱展示冷却'
              position:
                messages:
                  no-permission: '没有坐标展示权限'
                  cooldown: '坐标展示冷却'
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
        assertEquals("私聊关闭", loaded.privateMessageSettings().disabledMessage());
        assertEquals("没有回复目标", loaded.privateMessageSettings().noReplyTargetMessage());
        assertEquals("展示了 [物品]", loaded.itemShowcaseSettings().commandMessage());
        assertEquals("展示关闭", loaded.itemShowcaseSettings().disabledMessage());
        assertEquals("没有物品展示权限", loaded.itemShowcaseSettings().item().noPermissionMessage());
        assertEquals("没有背包内容", loaded.itemShowcaseSettings().inventory().emptyMessage());
        assertEquals("末影箱展示冷却", loaded.itemShowcaseSettings().enderChest().cooldownMessage());
        assertEquals("没有坐标展示权限", loaded.itemShowcaseSettings().position().noPermissionMessage());
        assertEquals("&#FF55FFPink CN", loaded.colorChatSettings().fixedSettings().findRgbColor("pink").display());
    }

    @Test
    void usesLanguageDefaultsWhenRuleMessagesAreOmitted() throws IOException {
        writeLocales("""
            anti-spam:
              messages:
                too-fast: '&c说话太快'
                too-long: '&c消息太长'
                too-many-caps: '&c大写太多'
                duplicate: '&c重复消息'
            filter:
              default-blocked-message: '&c内容被拦截'
            """, """
            anti-spam:
              messages:
                too-fast: '&cToo fast'
                too-long: '&cToo long'
                too-many-caps: '&cToo many caps'
                duplicate: '&cDuplicate'
            filter:
              default-blocked-message: '&cBlocked'
            """);

        LanguageService languageService = new LanguageService(tempDir.toFile());
        YamlConfiguration mainConfig = new YamlConfiguration();
        mainConfig.set("Options.Language", "zh_cn");
        languageService.reload(mainConfig);

        YamlConfiguration config = new YamlConfiguration();
        config.set("Filter.Enabled", true);
        config.set("Filter.Rules", List.of(java.util.Map.of(
            "id", "ad-block",
            "scope", "all",
            "mode", "block",
            "match", "(?i)(qq|vx)",
            "regex", true,
            "bypass-permission", "ymchat.filter.bypass"
        )));

        ChatPluginConfig loaded = new ChatConfigLoader(languageService).load(config);

        assertEquals("&c说话太快", loaded.antiSpamSettings().tooFastMessage());
        assertEquals("&c消息太长", loaded.antiSpamSettings().tooLongMessage());
        assertEquals("&c大写太多", loaded.antiSpamSettings().tooManyCapsMessage());
        assertEquals("&c重复消息", loaded.antiSpamSettings().duplicateMessage());
        assertEquals("&c内容被拦截", loaded.filterSettings().rules().getFirst().message());
    }

    @Test
    void explicitLegacyRuleMessagesOverrideLanguageDefaults() throws IOException {
        writeLocales("""
            anti-spam:
              messages:
                too-fast: '&c语言太快'
                too-long: '&c语言太长'
                too-many-caps: '&c语言大写'
                duplicate: '&c语言重复'
            filter:
              default-blocked-message: '&c语言拦截'
            """, """
            anti-spam:
              messages:
                too-fast: '&cToo fast'
                too-long: '&cToo long'
                too-many-caps: '&cToo many caps'
                duplicate: '&cDuplicate'
            filter:
              default-blocked-message: '&cBlocked'
            """);

        LanguageService languageService = new LanguageService(tempDir.toFile());
        YamlConfiguration mainConfig = new YamlConfiguration();
        mainConfig.set("Options.Language", "zh_cn");
        languageService.reload(mainConfig);

        YamlConfiguration config = new YamlConfiguration();
        config.set("Anti-Spam.Messages.Too-Fast", "&e旧配置太快");
        config.set("Filter.Enabled", true);
        config.set("Filter.Rules", List.of(java.util.Map.of(
            "id", "ad-block",
            "scope", "all",
            "mode", "block",
            "match", "(?i)(qq|vx)",
            "regex", true,
            "message", "&e旧配置拦截",
            "bypass-permission", "ymchat.filter.bypass"
        )));

        ChatPluginConfig loaded = new ChatConfigLoader(languageService).load(config);

        assertEquals("&e旧配置太快", loaded.antiSpamSettings().tooFastMessage());
        assertEquals("&e旧配置拦截", loaded.filterSettings().rules().getFirst().message());
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
    void loadsAntiSpamCapsMinimumLetterThreshold() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Anti-Spam.Caps-Ratio", 0.7D);
        config.set("Anti-Spam.Caps-Min-Letters", 12);

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(12, loaded.antiSpamSettings().capsMinLetters());
    }

    @Test
    void loadsMentionPlainNameMatchingSwitch() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Mentions.Match-Plain-Names", false);

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(false, loaded.mentionSettings().matchPlainNames());
    }

    @Test
    void loadsNameColorFixedSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Name-Color.Fixed.enabled", true);
        config.set("Name-Color.Fixed.rgb-colors", List.of(java.util.Map.of(
            "id", "mint",
            "display", "&#55FFAAName Mint",
            "permission", "ymchat.namecolor.rgb.mint",
            "value", "#55FFAA"
        )));

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(true, loaded.nameColorSettings().enabled());
        assertEquals("ymchat.namecolor.rgb.mint", loaded.nameColorSettings().findRgbColor("mint").permission());
        assertEquals("#55FFAA", loaded.nameColorSettings().findRgbColor("mint").value());
    }

    @Test
    void loadsFilterCloudSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Filter.Enabled", true);
        config.set("Filter.Cloud.Enabled", true);
        config.set("Filter.Cloud.Url", "https://example.invalid/database.json");
        config.set("Filter.Cloud.Array-Path", "words");
        config.set("Filter.Cloud.Refresh-Minutes", 15);
        config.set("Filter.Cloud.Mode", "replace");
        config.set("Filter.Cloud.Replacement", "[blocked]");
        config.set("Filter.Cloud.Bypass-Permission", "ymchat.filter.custom");
        config.set("Filter.Cloud.Scopes", List.of("public"));

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(true, loaded.filterSettings().enabled());
        assertEquals(true, loaded.filterSettings().cloudSettings().enabled());
        assertEquals("https://example.invalid/database.json", loaded.filterSettings().cloudSettings().url());
        assertEquals("words", loaded.filterSettings().cloudSettings().arrayPath());
        assertEquals(15, loaded.filterSettings().cloudSettings().refreshMinutes());
        assertEquals("replace", loaded.filterSettings().cloudSettings().mode());
        assertEquals("[blocked]", loaded.filterSettings().cloudSettings().replacement());
        assertEquals("ymchat.filter.custom", loaded.filterSettings().cloudSettings().bypassPermission());
        assertEquals(List.of("public"), loaded.filterSettings().cloudSettings().scopes());
    }

    @Test
    void loadsChannelSwitchSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Channels.Switch.Enabled", true);
        config.set("Channels.Switch.Admin-Permission", "ymchat.channel.custom");
        config.set("Channels.Switch.Cross-Server-Admin-Only", false);

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(true, loaded.channelSwitchSettings().enabled());
        assertEquals("ymchat.channel.custom", loaded.channelSwitchSettings().adminPermission());
        assertEquals(false, loaded.channelSwitchSettings().crossServerAdminOnly());
    }

    @Test
    void inheritedFormatCanFallbackAcrossChannelsByPreferredId() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Channels.List", List.of(
            java.util.Map.of(
                "id", "global",
                "format", "default"
            ),
            java.util.Map.of(
                "id", "cross-server",
                "format", "default",
                "cross-server", true
            )
        ));
        config.set("Channels.Formats", List.of(java.util.Map.of(
            "id", "default",
            "channel", "global",
            "priority", 100,
            "message", java.util.Map.of(
                "variants", List.of(java.util.Map.of("text", "&f: {message}"))
            )
        )));

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals("default", loaded.firstMatching(null, "cross-server", "default").id());
    }

    @Test
    void loadsPublicHighlightSettingsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("Highlights.Enabled", true);
        config.set("Highlights.Default-Channels", List.of("global", "trade"));
        config.set("Highlights.Keyword-Rules", List.of(java.util.Map.ofEntries(
            java.util.Map.entry("id", "buy"),
            java.util.Map.entry("enabled", true),
            java.util.Map.entry("priority", 120),
            java.util.Map.entry("channels", List.of("global")),
            java.util.Map.entry("type", "literal"),
            java.util.Map.entry("match", "buy"),
            java.util.Map.entry("matches", List.of("buy", "sell")),
            java.util.Map.entry("color", "&#FFD166"),
            java.util.Map.entry("formats", List.of("bold")),
            java.util.Map.entry("hover", List.of("&eTrade")),
            java.util.Map.entry("suggest", "/msg %player_name% I have ")
        )));
        config.set("Highlights.Pattern-Rules.price.enabled", true);
        config.set("Highlights.Pattern-Rules.price.regexes", List.of("\\b\\d+\\b"));
        config.set("Highlights.Pattern-Rules.price.hover", List.of("&ePrice"));
        config.set("Highlights.Pattern-Rules.price.copy", "%message%");

        ChatPluginConfig loaded = new ChatConfigLoader().load(config);

        assertEquals(List.of("global", "trade"), loaded.publicChatHighlightSettings().defaultChannels());
        assertEquals("buy", loaded.publicChatHighlightSettings().keywordRules().getFirst().id());
        assertEquals("buy", loaded.publicChatHighlightSettings().keywordRules().getFirst().match());
        assertEquals(List.of("buy", "sell"), loaded.publicChatHighlightSettings().keywordRules().getFirst().matches());
        assertEquals(List.of("&eTrade"), loaded.publicChatHighlightSettings().keywordRules().getFirst().hover());
        assertEquals("/msg %player_name% I have ", loaded.publicChatHighlightSettings().keywordRules().getFirst().suggest());
        assertEquals("price", loaded.publicChatHighlightSettings().patternRules().getFirst().id());
        assertEquals(List.of("&ePrice"), loaded.publicChatHighlightSettings().patternRules().getFirst().hover());
        assertEquals("%message%", loaded.publicChatHighlightSettings().patternRules().getFirst().copy());
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
