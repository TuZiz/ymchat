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
    void defaultResourcesUseFourMainConfigFiles() {
        List<String> expected = List.of("config.yml", "formats.yml", "rules.yml", "features.yml");
        List<String> missing = expected.stream()
            .filter(file -> !Files.exists(RESOURCES.resolve(file)))
            .toList();
        List<String> extra = List.of(
            "private-messages.yml",
            "color-chat.yml",
            "highlights.yml",
            "item-showcase.yml",
            "mentions.yml",
            "anti-spam.yml",
            "filter.yml",
            "cross-server.yml"
        ).stream()
            .filter(file -> Files.exists(RESOURCES.resolve(file)))
            .toList();

        assertTrue(missing.isEmpty(), () -> "Default resources must include the four main config files: " + missing);
        assertTrue(extra.isEmpty(), () -> "Default resources must not ship old split config files: " + extra);
    }

    @Test
    void defaultChannelFilesUseSemanticNamesWithoutNumericPrefixes() throws IOException {
        try (var stream = Files.walk(RESOURCES.resolve("channels"))) {
            List<String> numbered = stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("\\d+-.+"))
                .toList();

            assertTrue(numbered.isEmpty(), () -> "Default channel files must not use numeric prefixes: " + numbered);
        }
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
    void defaultConfigResourcesKeepConfigTextInline() throws IOException {
        List<String> references = new ArrayList<>();
        for (Path file : defaultYamlFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.contains("lang:")) {
                references.add(RESOURCES.relativize(file).toString());
            }
        }

        assertTrue(references.isEmpty(), () -> "Default config resources must not use lang: references: " + references);
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
