package ym.ymchat.service.language;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LanguageService {

    private static final String DEFAULT_LOCALE = "zh_cn";
    private static final String REFERENCE_PREFIX = "lang:";
    private static final String MISSING_TEXT = "&#FF5555\u8bed\u8a00\u6587\u672c\u7f3a\u5931\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u3002";
    private static final Logger LOGGER = Logger.getLogger(LanguageService.class.getName());

    private final File dataFolder;
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();
    private String activeLocale = DEFAULT_LOCALE;
    private YamlConfiguration activeBundle = new YamlConfiguration();
    private YamlConfiguration defaultBundle = new YamlConfiguration();

    public LanguageService(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void reload(FileConfiguration config) {
        String configuredLocale = normalizeLocale(config == null ? null : config.getString("Options.Language", DEFAULT_LOCALE));
        defaultBundle = loadBundle(DEFAULT_LOCALE);
        activeLocale = configuredLocale;
        activeBundle = DEFAULT_LOCALE.equals(configuredLocale) ? defaultBundle : loadBundle(configuredLocale);
        warnedMissingKeys.clear();
    }

    public String activeLocale() {
        return activeLocale;
    }

    public String get(String key, String... placeholders) {
        return applyPlaceholders(resolveString(key), placeholders);
    }

    public List<String> getList(String key, String... placeholders) {
        List<String> values = resolveList(key);
        List<String> resolved = new ArrayList<>(values.size());
        for (String value : values) {
            resolved.add(applyPlaceholders(value, placeholders));
        }
        return List.copyOf(resolved);
    }

    public String resolveConfigured(String configuredValue, String... placeholders) {
        if (configuredValue == null) {
            return null;
        }
        if (isReference(configuredValue)) {
            return get(referenceKey(configuredValue), placeholders);
        }
        return applyPlaceholders(configuredValue, placeholders);
    }

    public List<String> resolveConfiguredList(List<String> configuredValues, String... placeholders) {
        if (configuredValues == null || configuredValues.isEmpty()) {
            return List.of();
        }
        if (configuredValues.size() == 1 && isReference(configuredValues.getFirst())) {
            String key = referenceKey(configuredValues.getFirst());
            if (hasList(key)) {
                return getList(key, placeholders);
            }
            return List.of(get(key, placeholders));
        }

        List<String> resolved = new ArrayList<>(configuredValues.size());
        for (String value : configuredValues) {
            resolved.add(resolveConfigured(value, placeholders));
        }
        return List.copyOf(resolved);
    }

    private boolean hasList(String key) {
        return activeBundle.isList(key) || defaultBundle.isList(key);
    }

    private String resolveString(String key) {
        Object activeValue = activeBundle.get(key);
        if (activeValue instanceof String string) {
            return string;
        }
        if (activeValue instanceof List<?> activeList && !activeList.isEmpty()) {
            return String.valueOf(activeList.getFirst());
        }
        if (!DEFAULT_LOCALE.equals(activeLocale)) {
            Object defaultValue = defaultBundle.get(key);
            if (defaultValue instanceof String string) {
                return string;
            }
            if (defaultValue instanceof List<?> defaultList && !defaultList.isEmpty()) {
                return String.valueOf(defaultList.getFirst());
            }
        }
        warnMissing(key);
        return MISSING_TEXT;
    }

    private List<String> resolveList(String key) {
        Object activeValue = activeBundle.get(key);
        if (activeValue instanceof List<?> activeList) {
            return toStringList(activeList);
        }
        if (activeValue instanceof String string) {
            return List.of(string);
        }

        if (!DEFAULT_LOCALE.equals(activeLocale)) {
            Object defaultValue = defaultBundle.get(key);
            if (defaultValue instanceof List<?> defaultList) {
                return toStringList(defaultList);
            }
            if (defaultValue instanceof String string) {
                return List.of(string);
            }
        }
        warnMissing(key);
        return List.of(MISSING_TEXT);
    }

    private List<String> toStringList(List<?> source) {
        List<String> result = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return List.copyOf(result);
    }

    private YamlConfiguration loadBundle(String locale) {
        File file = new File(new File(dataFolder, "lang"), locale + ".yml");
        if (!file.isFile()) {
            return new YamlConfiguration();
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ignored) {
            return new YamlConfiguration();
        }
    }

    private void warnMissing(String key) {
        if (key != null && warnedMissingKeys.add(activeLocale + ":" + key)) {
            LOGGER.warning("YmChat language key is missing for locale " + activeLocale + ": " + key);
        }
    }

    private String applyPlaceholders(String value, String... placeholders) {
        if (value == null || placeholders == null || placeholders.length == 0) {
            return value;
        }
        String resolved = value;
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            String placeholder = placeholders[index];
            String replacement = placeholders[index + 1] == null ? "" : placeholders[index + 1];
            if (placeholder != null && !placeholder.isBlank()) {
                resolved = resolved.replace("%" + placeholder + "%", replacement);
            }
        }
        return resolved;
    }

    private boolean isReference(String value) {
        return value != null && value.regionMatches(true, 0, REFERENCE_PREFIX, 0, REFERENCE_PREFIX.length());
    }

    private String referenceKey(String value) {
        return value.substring(REFERENCE_PREFIX.length()).trim();
    }

    private String normalizeLocale(String input) {
        if (input == null || input.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "zh", "cn" -> "zh_cn";
            case "en" -> "en_us";
            default -> normalized;
        };
    }
}
