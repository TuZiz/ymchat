package ym.ymchat.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LocalizationResourceTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Pattern LANG_REFERENCE = Pattern.compile("^lang:([A-Za-z0-9_.-]+)$");
    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern NON_RGB_AMPERSAND = Pattern.compile("&(?!#)");
    private static final Set<String> ALLOWED_WORDS = Set.of(
        "YmChat",
        "ymchat",
        "reload",
        "join",
        "leave",
        "status",
        "channel",
        "debug",
        "color",
        "namecolor",
        "code",
        "preset",
        "on",
        "off",
        "reset",
        "megaphone",
        "chat",
        "title",
        "bossbar",
        "balance",
        "give",
        "take",
        "logs",
        "player",
        "keyword",
        "since",
        "page",
        "limit"
    );
    private static final Set<String> PLACEHOLDER_COMMENT_FRAGMENTS = Set.of(
        "\u914d\u7f6e\u9879\u8bf4\u660e",
        "\u914d\u7f6e\u6bb5\u8bf4\u660e",
        "\u5217\u8868\u9879\u914d\u7f6e",
        "\u914d\u7f6e\u884c\u8bf4\u660e",
        "TODO",
        "placeholder"
    );

    @Test
    void zhCnContainsEveryDefaultLangReference() throws IOException {
        YamlConfiguration zhCn = load("lang/zh_cn.yml");
        Set<String> references = new TreeSet<>();
        for (Path file : defaultYamlFiles()) {
            collectLangReferences(load(file), references);
        }

        List<String> missing = references.stream()
            .filter(reference -> !zhCn.contains(reference))
            .toList();

        assertTrue(missing.isEmpty(), () -> "zh_cn.yml is missing referenced keys: " + missing);
    }

    @Test
    void allLanguageFilesContainEveryDefaultLangReference() throws IOException {
        Set<String> references = new TreeSet<>();
        for (Path file : defaultYamlFiles()) {
            collectLangReferences(load(file), references);
        }

        List<String> missing = new ArrayList<>();
        for (Path file : languageYamlFiles()) {
            YamlConfiguration language = load(file);
            for (String reference : references) {
                if (!language.contains(reference)) {
                    missing.add(RESOURCES.relativize(file) + ":" + reference);
                }
            }
        }

        assertTrue(missing.isEmpty(), () -> "Language files are missing referenced keys: " + missing);
    }

    @Test
    void zhCnPlayerVisibleTextDoesNotContainEnglishWords() throws IOException {
        YamlConfiguration zhCn = load("lang/zh_cn.yml");
        List<String> leaks = new ArrayList<>();
        collectEnglishLeaks(zhCn, "", leaks);

        assertTrue(leaks.isEmpty(), () -> "zh_cn.yml contains unexpected English words: " + leaks);
    }

    @Test
    void languageMessagesUseRgbColorCodesOnly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : languageYamlFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (NON_RGB_AMPERSAND.matcher(line).find()) {
                    violations.add(RESOURCES.relativize(file) + ":" + (index + 1) + "=" + line.trim());
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Language files must use RGB color codes only: " + violations);
    }

    @Test
    void defaultResourcesDoNotShipSplitConfigFiles() throws IOException {
        Path splitConfigDirectory = RESOURCES.resolve("config");
        if (!Files.exists(splitConfigDirectory)) {
            return;
        }
        try (var stream = Files.walk(splitConfigDirectory)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            assertTrue(files.isEmpty(), () -> "Default resources must not ship config/*.yml files: " + files);
        }
    }

    @Test
    void defaultResourcesUseCompactMainConfigFiles() {
        List<String> expected = List.of("config.yml", "colors.yml", "rules.yml", "highlights.yml");
        List<String> missing = expected.stream()
            .filter(file -> !Files.exists(RESOURCES.resolve(file)))
            .toList();
        List<String> extra = List.of(
            "formats.yml",
            "features.yml",
            "private-messages.yml",
            "color-chat.yml",
            "name-color.yml",
            "item-showcase.yml",
            "mentions.yml",
            "anti-spam.yml",
            "filter.yml",
            "cross-server.yml"
        ).stream()
            .filter(file -> Files.exists(RESOURCES.resolve(file)))
            .toList();

        assertTrue(missing.isEmpty(), () -> "Default resources must include compact config files: " + missing);
        assertTrue(extra.isEmpty(), () -> "Default resources must not ship old split config files: " + extra);
    }

    @Test
    void defaultRuleMessagesLiveInLanguageFiles() throws IOException {
        YamlConfiguration rules = load("rules.yml");
        List<String> inlineMessages = new ArrayList<>();

        if (rules.isConfigurationSection("Anti-Spam.Messages")) {
            inlineMessages.add("Anti-Spam.Messages");
        }
        for (var rule : rules.getMapList("Filter.Rules")) {
            if (rule.containsKey("message")) {
                Object id = rule.get("id");
                inlineMessages.add("Filter.Rules." + (id == null ? "<unknown>" : id) + ".message");
            }
        }

        assertTrue(inlineMessages.isEmpty(),
            () -> "Default rules.yml must not inline user-facing messages; put them in lang/*.yml: " + inlineMessages);
    }

    @Test
    void defaultChannelFolderKeepsPerChannelFilesOnly() throws IOException {
        Path channels = RESOURCES.resolve("channels");
        List<String> expected = List.of("global.yml", "cross-server.yml", "world.yml", "staff.yml");
        List<String> missing = expected.stream()
            .filter(file -> !Files.exists(channels.resolve(file)))
            .toList();
        List<String> extra;
        try (var stream = Files.walk(channels)) {
            extra = stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> !expected.contains(name))
                .toList();
        }

        assertTrue(missing.isEmpty(), () -> "Default channel folder must keep channel files: " + missing);
        assertTrue(extra.isEmpty(), () -> "Default channel folder must not ship channel settings or extra files: " + extra);
    }

    @Test
    void defaultPublicChatFormatsLiveInChannelFiles() throws IOException {
        YamlConfiguration config = load("config.yml");
        List<String> missing = new ArrayList<>();
        for (String channel : List.of("global", "cross-server", "world", "staff")) {
            YamlConfiguration channelConfig = load("channels/" + channel + ".yml");
            if (!channelConfig.isConfigurationSection("Format")) {
                missing.add(channel + ".yml");
            }
        }

        assertTrue(!config.isList("Formats"), "config.yml must not keep public chat Formats; put them in channels/*.yml");
        assertTrue(missing.isEmpty(), () -> "Default channel files are missing Format sections: " + missing);
    }

    @Test
    void defaultCrossServerSettingsLiveInCrossServerChannel() throws IOException {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration global = load("channels/global.yml");
        YamlConfiguration crossServer = load("channels/cross-server.yml");

        assertTrue(!config.isConfigurationSection("Cross-Server"),
            "config.yml must not keep Cross-Server; put it in the cross-server channel file");
        assertTrue(!global.isConfigurationSection("Cross-Server"),
            "channels/global.yml must remain the default non-cross-server channel");
        assertTrue(!global.getBoolean("cross-server"),
            "channels/global.yml must not publish to cross-server chat");
        assertTrue(crossServer.isConfigurationSection("Cross-Server"),
            "channels/cross-server.yml must define Cross-Server settings for the cross-server channel");
        assertTrue(crossServer.getBoolean("cross-server"),
            "channels/cross-server.yml must be the bundled cross-server channel");
        assertTrue(!crossServer.getBoolean("Cross-Server.Enabled"),
            "channels/cross-server.yml must default to local-only chat until Cross-Server.Enabled is turned on");
    }

    @Test
    void defaultMegaphoneSettingsLiveInMegaphoneChannel() throws IOException {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration world = load("channels/world.yml");

        assertTrue(!config.isConfigurationSection("Megaphone"),
            "config.yml must not keep Megaphone; put it in the megaphone/world channel file");
        assertTrue(world.isConfigurationSection("Megaphone"),
            "channels/world.yml must define Megaphone settings for the megaphone channel");
        assertTrue(world.getBoolean("Megaphone.Capture-World-Channel"),
            "channels/world.yml must keep world-channel megaphone capture enabled by default");
    }

    @Test
    void defaultColorSettingsLiveInColorsFile() throws IOException {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration colors = load("colors.yml");

        assertTrue(!config.isConfigurationSection("Color-Chat"),
            "config.yml must not keep Color-Chat; put color settings in colors.yml");
        assertTrue(!config.isConfigurationSection("Name-Color"),
            "config.yml must not keep Name-Color; put color settings in colors.yml");
        assertTrue(colors.isConfigurationSection("Color-Chat"),
            "colors.yml must define Color-Chat settings");
        assertTrue(colors.isConfigurationSection("Name-Color"),
            "colors.yml must define Name-Color settings");
        assertTrue(!colors.getMapList("Colors.rgb-colors").isEmpty(),
            "colors.yml must define shared RGB colors");
    }

    @Test
    void defaultResourcesDoNotShipTradeChannel() throws IOException {
        Path tradeChannel = RESOURCES.resolve("channels/trade.yml");
        List<String> references = new ArrayList<>();
        try (var stream = Files.walk(RESOURCES)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("channels/trade.yml")
                    || content.contains("channels.defaults.trade")
                    || content.contains("formats.trade")
                    || content.contains("channel:trade")
                    || content.contains("\u9891\u9053:\u4ea4\u6613")) {
                    references.add(RESOURCES.relativize(file).toString());
                }
            }
        }

        assertTrue(!Files.exists(tradeChannel), () -> "Default resources must not ship the trade channel: " + tradeChannel);
        assertTrue(references.isEmpty(), () -> "Default resources must not reference the trade channel: " + references);
    }

    @Test
    void defaultResourcesDoNotShipColorMenuGui() {
        Path colorGui = RESOURCES.resolve("gui/main.yml");

        assertTrue(!Files.exists(colorGui), () -> "Default resources must not ship the removed color GUI: " + colorGui);
    }

    @Test
    void defaultResourcesDoNotReferenceRemovedColorGuiLanguageKeys() throws IOException {
        List<String> references = new ArrayList<>();
        try (var stream = Files.walk(RESOURCES)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("lang:gui.color") || content.contains("gui.color.")) {
                    references.add(RESOURCES.relativize(file).toString());
                }
            }
        }

        assertTrue(references.isEmpty(), () -> "Default resources must not reference removed color GUI language keys: " + references);
    }

    @Test
    void defaultGuiResourcesKeepTextInline() throws IOException {
        Path guiDirectory = RESOURCES.resolve("gui");
        if (!Files.exists(guiDirectory)) {
            return;
        }
        List<String> references = new ArrayList<>();
        try (var stream = Files.walk(guiDirectory)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.contains("lang:")) {
                    references.add(RESOURCES.relativize(file).toString());
                }
            }
        }

        assertTrue(references.isEmpty(), () -> "GUI default resources must keep display text inline: " + references);
    }

    @Test
    void defaultConfigCommentsDescribeConcreteOptions() throws IOException {
        List<String> placeholders = new ArrayList<>();
        for (Path file : defaultYamlFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String trimmed = lines.get(index).trim();
                if (!trimmed.startsWith("#")) {
                    continue;
                }
                if (PLACEHOLDER_COMMENT_FRAGMENTS.stream().anyMatch(trimmed::contains)) {
                    placeholders.add(RESOURCES.relativize(file) + ":" + (index + 1) + "=" + trimmed);
                }
            }
        }

        assertTrue(placeholders.isEmpty(), () -> "Default config comments must describe concrete behavior: " + placeholders);
    }

    @Test
    void readmeDoesNotRecommendSplitConfigDirectory() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        assertTrue(!readme.contains("Feature settings live in `config/*.yml`"));
        assertTrue(!readme.contains("Files:\n  Channels: 'config/channels.yml'"));
        assertTrue(!readme.contains("gui/main.yml"));
        assertTrue(!readme.contains("color GUI"));
    }

    private List<Path> defaultYamlFiles() throws IOException {
        try (var stream = Files.walk(RESOURCES)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .filter(path -> !path.toString().contains("\\lang\\"))
                .toList();
        }
    }

    private List<Path> languageYamlFiles() throws IOException {
        try (var stream = Files.walk(RESOURCES.resolve("lang"))) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .toList();
        }
    }

    private YamlConfiguration load(String relativePath) throws IOException {
        return load(RESOURCES.resolve(relativePath));
    }

    private YamlConfiguration load(Path path) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Could not load " + path, exception);
        }
        return configuration;
    }

    private void collectLangReferences(ConfigurationSection section, Set<String> references) {
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof String string) {
                addLangReference(string, references);
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element != null) {
                        addLangReference(element.toString(), references);
                    }
                }
            }
        }
    }

    private void addLangReference(String value, Set<String> references) {
        Matcher matcher = LANG_REFERENCE.matcher(value == null ? "" : value.trim());
        if (matcher.matches()) {
            references.add(matcher.group(1));
        }
    }

    private void collectEnglishLeaks(ConfigurationSection section, String prefix, List<String> leaks) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isBlank() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                collectEnglishLeaks(child, path, leaks);
            } else if (value instanceof String string) {
                collectEnglishLeak(path, string, leaks);
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element != null) {
                        collectEnglishLeak(path, element.toString(), leaks);
                    }
                }
            }
        }
    }

    private void collectEnglishLeak(String path, String value, List<String> leaks) {
        String stripped = stripTechnicalText(value);
        Matcher matcher = ENGLISH_WORD.matcher(stripped);
        while (matcher.find()) {
            String word = matcher.group();
            if (!ALLOWED_WORDS.contains(word)) {
                leaks.add(path + "=" + value);
                return;
            }
        }
    }

    private String stripTechnicalText(String input) {
        return (input == null ? "" : input)
            .replaceAll("%[^%]+%", "")
            .replaceAll("\\{[^}]+}", "")
            .replaceAll("&#[0-9A-Fa-f]{6}", "")
            .replaceAll("&[0-9A-Fa-fK-ORk-or]", "")
            .replaceAll("/[0-9A-Za-z_:-]+", "")
            .replaceAll("\\[[0-9A-Za-z_-]+]", "")
            .replaceAll("\\b[0-9]+[mhd]\\b", "");
    }
}
