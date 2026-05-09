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
    private static final List<String> DEFAULT_CHANNEL_RESOURCES = List.of(
        "channels/settings.yml",
        "channels/global.yml",
        "channels/world.yml",
        "channels/staff.yml"
    );

    static {
        DEFAULT_FILES.put("Channels", "channels");
        DEFAULT_FILES.put("Formats", "formats.yml");
        DEFAULT_FILES.put("Private-Messages", "features.yml");
        DEFAULT_FILES.put("Color-Chat", "config.yml");
        DEFAULT_FILES.put("Highlights", "rules.yml");
        DEFAULT_FILES.put("Item-Showcase", "features.yml");
        DEFAULT_FILES.put("Megaphone", "features.yml");
        DEFAULT_FILES.put("Mentions", "config.yml");
        DEFAULT_FILES.put("Anti-Spam", "rules.yml");
        DEFAULT_FILES.put("Filter", "rules.yml");
        DEFAULT_FILES.put("Cross-Server", "config.yml");
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
        if (rootConfig == null || !rootConfig.isConfigurationSection("Files")) {
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
            String configuredPath = rootConfig.getString("Files." + sectionName, defaultPath);
            File file = resolveDataFile(configuredPath);
            if ("Channels".equals(sectionName) && file.isDirectory()) {
                copyChannelsDirectory(file, merged);
            } else if (file.isFile()) {
                YamlConfiguration child = loadYaml(file);
                copySplitSection(sectionName, child, merged);
            } else if (!merged.contains(sectionName)) {
                warnMissingSplitFile(configuredPath, sectionName);
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
            if (splitConfig != null) {
                copySplitSection(sectionName, splitConfig, merged);
            }
        }
        return merged;
    }

    public static Map<String, String> defaultFiles() {
        return DEFAULT_FILES;
    }

    private void copyRootWithoutFiles(FileConfiguration rootConfig, YamlConfiguration merged) {
        for (String key : rootConfig.getKeys(false)) {
            if ("Files".equalsIgnoreCase(key)) {
                continue;
            }
            copyValue(rootConfig, merged, key);
        }
    }

    private void copySplitSection(String sectionName, FileConfiguration child, YamlConfiguration merged) {
        Object rootValue = child.get(sectionName);
        if (rootValue != null && !(rootValue instanceof ConfigurationSection)) {
            merged.set(sectionName, rootValue);
            return;
        }
        ConfigurationSection section = child.getConfigurationSection(sectionName);
        if (section == null) {
            section = child;
        }
        merged.set(sectionName, null);
        copySection(section, merged.createSection(sectionName));
    }

    private void copyBundledDefaultSection(
        String sectionName,
        String defaultPath,
        FileConfiguration bundledDefault,
        YamlConfiguration merged
    ) {
        if ("Channels".equals(sectionName)) {
            copyBundledDefaultChannels(merged);
            if (merged.contains("Channels")) {
                return;
            }
        } else {
            YamlConfiguration bundledSplit = loadBundledYaml(defaultPath);
            if (!bundledSplit.getKeys(false).isEmpty()) {
                copySplitSection(sectionName, bundledSplit, merged);
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
        merged.set("Channels", null);
        copySection(channels, merged.createSection("Channels"));
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
        merged.set("Channels", null);
        copySection(channels, merged.createSection("Channels"));
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
                if ("Show-Display".equalsIgnoreCase(key) || "Default".equalsIgnoreCase(key)) {
                    continue;
                }
                channel.put(key, source.get(key));
            }
            list.add(channel);
        }
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
            case "world.yml", "world.yaml" -> "02-" + name;
            case "staff.yml", "staff.yaml" -> "03-" + name;
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
