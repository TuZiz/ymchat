package ym.ymchat.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatConfigFileLoader {

    private static final Map<String, String> DEFAULT_FILES = new LinkedHashMap<>();
    private static final Map<String, String> LEGACY_DEFAULT_FILES = new LinkedHashMap<>();
    private static final List<String> DEFAULT_CHANNEL_RESOURCES = List.of(
        "channels/global.yml",
        "channels/cross-server.yml",
        "channels/world.yml",
        "channels/staff.yml"
    );

    static {
        DEFAULT_FILES.put("Channels", "channels");
        DEFAULT_FILES.put("Formats", "config.yml");
        DEFAULT_FILES.put("Private-Messages", "config.yml");
        DEFAULT_FILES.put("Colors", "colors.yml");
        DEFAULT_FILES.put("Color-Chat", "colors.yml");
        DEFAULT_FILES.put("Name-Color", "colors.yml");
        DEFAULT_FILES.put("Highlights", "highlights.yml");
        DEFAULT_FILES.put("Item-Showcase", "config.yml");
        DEFAULT_FILES.put("Megaphone", "channels");
        DEFAULT_FILES.put("Mentions", "config.yml");
        DEFAULT_FILES.put("Anti-Spam", "rules.yml");
        DEFAULT_FILES.put("Filter", "rules.yml");
        DEFAULT_FILES.put("Cross-Server", "channels");

        LEGACY_DEFAULT_FILES.put("Channels", "channels");
        LEGACY_DEFAULT_FILES.put("Formats", "formats.yml");
        LEGACY_DEFAULT_FILES.put("Private-Messages", "features.yml");
        LEGACY_DEFAULT_FILES.put("Highlights", "rules.yml");
        LEGACY_DEFAULT_FILES.put("Item-Showcase", "features.yml");
        LEGACY_DEFAULT_FILES.put("Megaphone", "features.yml");
    }

    private final File dataFolder;
    private final Function<String, InputStream> resourceProvider;
    private final Consumer<String> warningLogger;
    private final Set<String> warnedMissingFiles = new HashSet<>();
    private boolean warnedLegacySplitConfig;

    public ChatConfigFileLoader(JavaPlugin plugin) {
        this(
            plugin == null ? new File(".") : plugin.getDataFolder(),
            plugin == null ? path -> null : plugin::getResource,
            plugin == null ? message -> { } : plugin.getLogger()::warning
        );
    }

    ChatConfigFileLoader(File dataFolder, Function<String, InputStream> resourceProvider, Consumer<String> warningLogger) {
        this.dataFolder = dataFolder == null ? new File(".") : dataFolder;
        this.resourceProvider = resourceProvider == null ? path -> null : resourceProvider;
        this.warningLogger = warningLogger == null ? message -> { } : warningLogger;
    }

    public FileConfiguration load(FileConfiguration rootConfig) {
        if (rootConfig == null) {
            return rootConfig;
        }

        if (usesLegacyConfigFolder(rootConfig)) {
            warnLegacySplitConfig();
        }
        YamlConfiguration merged = new YamlConfiguration();
        YamlConfiguration bundledDefault = loadBundledDefaultConfig();
        copyRootWithoutFiles(rootConfig, merged);

        for (Map.Entry<String, String> entry : DEFAULT_FILES.entrySet()) {
            String sectionName = entry.getKey();
            String defaultPath = entry.getValue();
            String configuredPath = configuredSplitPath(rootConfig, sectionName, defaultPath);
            File file = resolveDataFile(configuredPath);
            boolean copied = false;
            boolean primaryRootConfig = !rootConfig.isConfigurationSection("Files") && "config.yml".equals(defaultPath);
            if ("Channels".equals(sectionName) && file.isDirectory()) {
                copyChannelsDirectory(file, merged);
                copyBundledOrRootChannelSettings(rootConfig, bundledDefault, merged);
                copied = true;
            } else if (file.isFile()) {
                YamlConfiguration child = loadYaml(file);
                if (shouldPreferLegacyHighlights(sectionName, defaultPath, rootConfig, child)) {
                    copied = copyLegacyDefaultSection(sectionName, merged);
                }
                if (!copied) {
                    copied = copySplitSection(sectionName, child, merged, primaryRootConfig);
                }
            }
            if (!copied && !rootConfig.isConfigurationSection("Files") && !merged.contains(sectionName)) {
                copied = copyLegacyDefaultSection(sectionName, merged);
            }
            if (!copied
                && "Channels".equals(sectionName)
                && !rootConfig.isConfigurationSection("Files")
                && merged.getMapList("Channels.List").isEmpty()) {
                copyBundledDefaultChannels(merged);
                copyBundledOrRootChannelSettings(rootConfig, bundledDefault, merged);
                copied = merged.contains("Channels");
            }
            if (!copied && !merged.contains(sectionName)) {
                if (rootConfig.isConfigurationSection("Files")) {
                    warnMissingSplitFile(configuredPath, sectionName);
                }
                copyBundledDefaultSection(sectionName, defaultPath, bundledDefault, merged);
            }
        }

        return merged;
    }

    public FileConfiguration merge(FileConfiguration rootConfig, Map<String, ? extends FileConfiguration> splitConfigs) {
        if (rootConfig == null || !rootConfig.isConfigurationSection("Files")) {
            return rootConfig;
        }

        YamlConfiguration merged = new YamlConfiguration();
        copyRootWithoutFiles(rootConfig, merged);
        for (String sectionName : DEFAULT_FILES.keySet()) {
            FileConfiguration splitConfig = splitConfigs == null ? null : splitConfigs.get(sectionName);
            if (splitConfig == null && ("Color-Chat".equals(sectionName) || "Name-Color".equals(sectionName))) {
                splitConfig = splitConfigs == null ? null : splitConfigs.get("Colors");
            }
            if (splitConfig != null) {
                copySplitSection(sectionName, splitConfig, merged);
            }
        }
        return merged;
    }

    public static Map<String, String> defaultFiles() {
        return DEFAULT_FILES;
    }

    private String configuredSplitPath(FileConfiguration rootConfig, String sectionName, String defaultPath) {
        if (rootConfig.isConfigurationSection("Files")) {
            if (("Color-Chat".equals(sectionName) || "Name-Color".equals(sectionName))
                && !rootConfig.contains("Files." + sectionName)
                && rootConfig.contains("Files.Colors")) {
                return rootConfig.getString("Files.Colors", defaultPath);
            }
            return rootConfig.getString("Files." + sectionName, defaultPath);
        }
        return defaultPath;
    }

    private void copyRootWithoutFiles(FileConfiguration rootConfig, YamlConfiguration merged) {
        for (String key : rootConfig.getKeys(false)) {
            if ("Files".equalsIgnoreCase(key)) {
                continue;
            }
            copyValue(rootConfig, merged, key);
        }
    }

    private void copyBundledOrRootChannelSettings(
        FileConfiguration rootConfig,
        FileConfiguration bundledDefault,
        YamlConfiguration merged
    ) {
        ConfigurationSection rootChannels = rootConfig == null ? null : rootConfig.getConfigurationSection("Channels");
        if (rootChannels != null) {
            copySectionValue(rootChannels, merged.getConfigurationSection("Channels"), "Show-Display");
            copySectionValue(rootChannels, merged.getConfigurationSection("Channels"), "Default");
            return;
        }
        ConfigurationSection bundledChannels = bundledDefault == null ? null : bundledDefault.getConfigurationSection("Channels");
        if (bundledChannels != null) {
            copySectionValue(bundledChannels, merged.getConfigurationSection("Channels"), "Show-Display");
            copySectionValue(bundledChannels, merged.getConfigurationSection("Channels"), "Default");
        }
    }

    private void copySplitSection(String sectionName, FileConfiguration child, YamlConfiguration merged) {
        copySplitSection(sectionName, child, merged, false);
    }

    private boolean copySplitSection(
        String sectionName,
        FileConfiguration child,
        YamlConfiguration merged,
        boolean requireNamedSection
    ) {
        Object rootValue = child.get(sectionName);
        if (rootValue != null && !(rootValue instanceof ConfigurationSection)) {
            merged.set(sectionName, rootValue);
            return true;
        }
        ConfigurationSection section = child.getConfigurationSection(sectionName);
        if (section == null && requireNamedSection) {
            return false;
        }
        if (section == null) {
            section = child;
        }
        merged.set(sectionName, null);
        copySection(section, merged.createSection(sectionName));
        return true;
    }

    private void copyBundledDefaultSection(
        String sectionName,
        String defaultPath,
        FileConfiguration bundledDefault,
        YamlConfiguration merged
    ) {
        if ("Cross-Server".equals(sectionName) && "channels".equals(defaultPath)) {
            copyBundledDefaultCrossServer(merged);
            if (merged.contains("Cross-Server")) {
                return;
            }
        }
        if ("Megaphone".equals(sectionName) && "channels".equals(defaultPath)) {
            copyBundledDefaultMegaphone(merged);
            if (merged.contains("Megaphone")) {
                return;
            }
        }
        YamlConfiguration bundledSplit = loadBundledYaml(defaultPath);
        if (bundledSplit.contains(sectionName)) {
            copySplitSection(sectionName, bundledSplit, merged, true);
            return;
        }
        if (!"config.yml".equals(defaultPath)) {
            if (!bundledSplit.getKeys(false).isEmpty()) {
                copySplitSection(sectionName, bundledSplit, merged);
                return;
            }
        }
        if ("Channels".equals(sectionName)) {
            copyBundledDefaultChannels(merged);
            copyBundledOrRootChannelSettings(null, bundledDefault, merged);
            if (merged.contains("Channels")) {
                return;
            }
        }
        if (bundledDefault == null) {
            return;
        }
        Object rootValue = bundledDefault.get(sectionName);
        if (rootValue == null) {
            return;
        }
        if (!(rootValue instanceof ConfigurationSection section)) {
            merged.set(sectionName, rootValue);
            return;
        }
        merged.set(sectionName, null);
        copySection(section, merged.createSection(sectionName));
    }

    private void copyBundledDefaultCrossServer(YamlConfiguration merged) {
        for (String resourcePath : DEFAULT_CHANNEL_RESOURCES) {
            ConfigurationSection crossServer = loadBundledYaml(resourcePath).getConfigurationSection("Cross-Server");
            if (crossServer == null) {
                continue;
            }
            merged.set("Cross-Server", null);
            copySection(crossServer, merged.createSection("Cross-Server"));
            return;
        }
    }

    private void copyBundledDefaultMegaphone(YamlConfiguration merged) {
        for (String resourcePath : DEFAULT_CHANNEL_RESOURCES) {
            ConfigurationSection megaphone = loadBundledYaml(resourcePath).getConfigurationSection("Megaphone");
            if (megaphone == null) {
                continue;
            }
            merged.set("Megaphone", null);
            copySection(megaphone, merged.createSection("Megaphone"));
            return;
        }
    }

    private boolean copyLegacyDefaultSection(String sectionName, YamlConfiguration merged) {
        String legacyPath = LEGACY_DEFAULT_FILES.get(sectionName);
        if (legacyPath == null || legacyPath.isBlank()) {
            return false;
        }
        File file = resolveDataFile(legacyPath);
        if ("Channels".equals(sectionName) && file.isDirectory()) {
            copyChannelsDirectory(file, merged);
            return merged.contains(sectionName);
        }
        if (!file.isFile()) {
            return false;
        }
        YamlConfiguration legacy = loadYaml(file);
        if ("Highlights".equals(sectionName)) {
            return legacy.contains(sectionName) && copySplitSection(sectionName, legacy, merged, true);
        }
        return copySplitSection(sectionName, legacy, merged, false);
    }

    private boolean shouldPreferLegacyHighlights(
        String sectionName,
        String defaultPath,
        FileConfiguration rootConfig,
        YamlConfiguration highlights
    ) {
        if (!"Highlights".equals(sectionName)
            || !"highlights.yml".equals(defaultPath)
            || rootConfig.isConfigurationSection("Files")
            || !matchesBundledSection(highlights, "highlights.yml", sectionName)) {
            return false;
        }
        File legacyRules = resolveDataFile("rules.yml");
        return legacyRules.isFile() && loadYaml(legacyRules).contains(sectionName);
    }

    private boolean matchesBundledSection(FileConfiguration current, String bundledPath, String sectionName) {
        if (current == null) {
            return false;
        }
        Object currentValue = sectionToValue(current.get(sectionName));
        Object bundledValue = sectionToValue(loadBundledYaml(bundledPath).get(sectionName));
        return currentValue == null ? bundledValue == null : currentValue.equals(bundledValue);
    }

    private void copyChannelsDirectory(File directory, YamlConfiguration merged) {
        List<File> files = channelFiles(directory);
        if (files.isEmpty()) {
            warnMissingSplitFile(directory.getPath(), "Channels");
            copyBundledDefaultChannels(merged);
            return;
        }

        YamlConfiguration channels = new YamlConfiguration();
        List<Object> list = new ArrayList<>();
        for (File file : files) {
            YamlConfiguration child = loadYaml(file);
            appendChannelFile(child, channels, list);
        }
        channels.set("List", list);
        copyChannelBundle(channels, merged);
    }

    private void copyBundledDefaultChannels(YamlConfiguration merged) {
        YamlConfiguration channels = new YamlConfiguration();
        List<Object> list = new ArrayList<>();
        for (String resourcePath : DEFAULT_CHANNEL_RESOURCES) {
            appendChannelFile(loadBundledYaml(resourcePath), channels, list);
        }
        if (channels.getKeys(false).isEmpty() && list.isEmpty()) {
            return;
        }
        channels.set("List", list);
        copyChannelBundle(channels, merged);
    }

    private void copyChannelBundle(YamlConfiguration channels, YamlConfiguration merged) {
        merged.set("Channels", null);
        ConfigurationSection targetChannels = merged.createSection("Channels");
        for (String key : channels.getKeys(false)) {
            if ("Cross-Server".equalsIgnoreCase(key)) {
                ConfigurationSection crossServer = channels.getConfigurationSection(key);
                if (crossServer != null) {
                    merged.set("Cross-Server", null);
                    copySection(crossServer, merged.createSection("Cross-Server"));
                }
                continue;
            }
            if ("Megaphone".equalsIgnoreCase(key)) {
                ConfigurationSection megaphone = channels.getConfigurationSection(key);
                if (megaphone != null && !merged.contains("Megaphone")) {
                    merged.set("Megaphone", null);
                    copySection(megaphone, merged.createSection("Megaphone"));
                }
                continue;
            }
            copyValue(channels, targetChannels, key);
        }
    }

    private void appendChannelFile(YamlConfiguration source, YamlConfiguration channels, List<Object> list) {
        if (source == null || source.getKeys(false).isEmpty()) {
            return;
        }
        ConfigurationSection fullSection = source.getConfigurationSection("Channels");
        if (fullSection != null) {
            copySectionValue(fullSection, channels, "Show-Display");
            copySectionValue(fullSection, channels, "Default");
            list.addAll(fullSection.getMapList("List"));
            return;
        }
        copySectionValue(source, channels, "Show-Display");
        copySectionValue(source, channels, "Default");
        if (source.contains("List")) {
            list.addAll(source.getMapList("List"));
            return;
        }
        if (source.contains("id")) {
            Map<String, Object> channel = new LinkedHashMap<>();
            for (String key : source.getKeys(false)) {
                if ("Show-Display".equalsIgnoreCase(key)
                    || "Default".equalsIgnoreCase(key)
                    || "Format".equalsIgnoreCase(key)
                    || "Cross-Server".equalsIgnoreCase(key)
                    || "Megaphone".equalsIgnoreCase(key)) {
                    continue;
                }
                channel.put(key, source.get(key));
            }
            appendCrossServerSettings(source, channels);
            appendMegaphoneSettings(source, channels);
            appendChannelFormat(source, channels, channel);
            list.add(channel);
        }
    }

    private void appendCrossServerSettings(ConfigurationSection source, YamlConfiguration channels) {
        ConfigurationSection crossServer = source.getConfigurationSection("Cross-Server");
        if (crossServer == null) {
            return;
        }
        channels.set("Cross-Server", null);
        copySection(crossServer, channels.createSection("Cross-Server"));
    }

    private void appendMegaphoneSettings(ConfigurationSection source, YamlConfiguration channels) {
        ConfigurationSection megaphone = source.getConfigurationSection("Megaphone");
        if (megaphone == null) {
            return;
        }
        channels.set("Megaphone", null);
        copySection(megaphone, channels.createSection("Megaphone"));
    }

    private void appendChannelFormat(
        ConfigurationSection source,
        YamlConfiguration channels,
        Map<String, Object> channel
    ) {
        ConfigurationSection formatSection = source.getConfigurationSection("Format");
        if (formatSection == null) {
            channel.putIfAbsent("format", "default");
            return;
        }
        String channelId = String.valueOf(channel.getOrDefault("id", ""));
        if (channelId.isBlank()) {
            return;
        }
        String inheritedFormat = formatSection.getString("inherit", "");
        if (!inheritedFormat.isBlank()) {
            channel.put("format", inheritedFormat);
            return;
        }
        String formatId = formatSection.getString("id", channelId);
        channel.put("format", formatId);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("id", formatId);
        format.put("channel", formatSection.getString("channel", channelId));
        for (String key : formatSection.getKeys(false)) {
            if ("id".equalsIgnoreCase(key) || "channel".equalsIgnoreCase(key)) {
                continue;
            }
            format.put(key, sectionToValue(formatSection.get(key)));
        }
        List<Object> formats = new ArrayList<>(channels.getMapList("Formats"));
        formats.add(format);
        channels.set("Formats", formats);
    }

    private Object sectionToValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, sectionToValue(section.get(key)));
            }
            return map;
        }
        if (value instanceof List<?> list) {
            List<Object> values = new ArrayList<>(list.size());
            for (Object element : list) {
                values.add(sectionToValue(element));
            }
            return values;
        }
        return value;
    }

    private void copySectionValue(ConfigurationSection source, ConfigurationSection target, String key) {
        if (source.contains(key)) {
            copyValue(source, target, key);
        }
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            copyValue(source, target, key);
        }
    }

    private void copyValue(ConfigurationSection source, ConfigurationSection target, String key) {
        Object value = source.get(key);
        if (value instanceof ConfigurationSection section) {
            ConfigurationSection child = target.createSection(key);
            copySection(section, child);
            return;
        }
        target.set(key, value);
    }

    private File resolveDataFile(String configuredPath) {
        String safePath = configuredPath == null || configuredPath.isBlank()
            ? ""
            : configuredPath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        return new File(dataFolder, safePath);
    }

    private List<File> channelFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile() && isYamlFile(file.getName()));
        if (files == null) {
            return List.of();
        }
        List<File> result = new ArrayList<>(List.of(files));
        result.sort(Comparator.comparing(this::channelSortKey, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private String channelSortKey(File file) {
        String name = file.getName().toLowerCase();
        return switch (name) {
            case "settings.yml", "settings.yaml" -> "00-" + name;
            case "global.yml", "global.yaml" -> "01-" + name;
            case "cross-server.yml", "cross-server.yaml" -> "02-" + name;
            case "world.yml", "world.yaml" -> "03-" + name;
            case "staff.yml", "staff.yaml" -> "04-" + name;
            default -> "50-" + name;
        };
    }

    private boolean isYamlFile(String name) {
        String lowered = name == null ? "" : name.toLowerCase();
        return lowered.endsWith(".yml") || lowered.endsWith(".yaml");
    }

    private YamlConfiguration loadYaml(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    private YamlConfiguration loadBundledYaml(String path) {
        try (InputStream input = resourceProvider.apply(path)) {
            if (input == null) {
                return new YamlConfiguration();
            }
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            warningLogger.accept("鐠囪褰囬崘鍛枂姒涙顓婚柊宥囩枂婢惰精瑙? " + path + "閿涘苯甯崶? " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private YamlConfiguration loadBundledDefaultConfig() {
        try (InputStream input = resourceProvider.apply("config.yml")) {
            if (input == null) {
                return new YamlConfiguration();
            }
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            warningLogger.accept("Failed to read bundled config.yml for split configuration fallback: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private boolean usesLegacyConfigFolder(FileConfiguration rootConfig) {
        ConfigurationSection files = rootConfig.getConfigurationSection("Files");
        if (files == null) {
            return false;
        }
        for (String key : files.getKeys(false)) {
            String path = files.getString(key, "");
            String normalized = path.replace('\\', '/').toLowerCase();
            if (normalized.startsWith("config/")) {
                return true;
            }
        }
        return false;
    }

    private void warnLegacySplitConfig() {
        if (warnedLegacySplitConfig) {
            return;
        }
        warnedLegacySplitConfig = true;
        warningLogger.accept("Detected legacy config/ split configuration. YmChat will keep reading it for compatibility; new defaults use root config files and the channels/ folder.");
    }

    private void warnMissingSplitFile(String configuredPath, String sectionName) {
        String path = configuredPath == null || configuredPath.isBlank() ? "(empty path)" : configuredPath;
        if (warnedMissingFiles.add(path)) {
            warningLogger.accept("Split configuration file does not exist: " + path + ". Using bundled default section: " + sectionName + ".");
        }
    }
}
