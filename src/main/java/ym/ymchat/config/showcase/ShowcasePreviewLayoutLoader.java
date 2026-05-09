package ym.ymchat.config.showcase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ym.ymchat.service.language.LanguageService;

public final class ShowcasePreviewLayoutLoader {

    private static final List<String> DEFAULT_INVENTORY_SHAPE = List.of(
        "##aaaao##",
        "sssssssss",
        "sssssssss",
        "sssssssss",
        "hhhhhhhhh",
        "###S#X###"
    );
    private static final List<String> DEFAULT_ENDER_CHEST_SHAPE = List.of(
        "#########",
        "eeeeeeeee",
        "eeeeeeeee",
        "eeeeeeeee",
        "###S#X###"
    );

    public ShowcasePreviewLayout load(FileConfiguration configuration, LanguageService languageService) {
        if (configuration == null) {
            return ShowcasePreviewLayout.defaults();
        }
        ShowcasePreviewLayout defaults = ShowcasePreviewLayout.defaults();
        Map<String, KeyDefinition> definitions = parseKeyDefinitions(configuration.getConfigurationSection("Key"), languageService);
        return new ShowcasePreviewLayout(
            parseView(configuration.getConfigurationSection("Inventory"), DEFAULT_INVENTORY_SHAPE, defaults.inventory(), definitions, languageService),
            parseView(configuration.getConfigurationSection("Ender-Chest"), DEFAULT_ENDER_CHEST_SHAPE, defaults.enderChest(), definitions, languageService),
            parseTypeDisplays(configuration.getConfigurationSection("Types"), defaults.typeDisplays(), languageService)
        );
    }

    private ShowcasePreviewLayout.ViewLayout parseView(
        ConfigurationSection section,
        List<String> fallbackShape,
        ShowcasePreviewLayout.ViewLayout defaults,
        Map<String, KeyDefinition> definitions,
        LanguageService languageService
    ) {
        List<String> shape = normalizeShape(section == null ? List.of() : section.getStringList("Shape"), fallbackShape);
        int size = shape.size() * 9;
        Map<String, List<Integer>> dynamicSlots = new LinkedHashMap<>();
        List<ShowcasePreviewLayout.StaticItemConfig> staticItems = new ArrayList<>();
        ShowcasePreviewLayout.GuiButtonConfig summary = defaults.summary();
        ShowcasePreviewLayout.GuiButtonConfig close = defaults.close();

        for (int row = 0; row < shape.size(); row++) {
            String line = shape.get(row);
            for (int column = 0; column < 9; column++) {
                String symbol = String.valueOf(line.charAt(column));
                int slot = row * 9 + column;
                if (" ".equals(symbol)) {
                    continue;
                }
                if (isDynamicSymbol(symbol)) {
                    dynamicSlots.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(slot);
                    continue;
                }
                KeyDefinition definition = definitions.get(symbol);
                if (definition == null) {
                    continue;
                }
                switch (definition.action()) {
                    case "summary" -> summary = new ShowcasePreviewLayout.GuiButtonConfig(slot, definition.material(), definition.name(), definition.lore());
                    case "close" -> close = new ShowcasePreviewLayout.GuiButtonConfig(slot, definition.material(), definition.name(), definition.lore());
                    default -> staticItems.add(new ShowcasePreviewLayout.StaticItemConfig(slot, definition.material(), definition.name(), definition.lore()));
                }
            }
        }

        String rawTitle = section == null
            ? defaults.title()
            : section.getString("Title", defaults.title());
        String title = resolveString(languageService, rawTitle);
        if (dynamicSlots.isEmpty()) {
            dynamicSlots = defaults.contentSlots();
        }
        if (staticItems.isEmpty()) {
            staticItems = defaults.staticItems();
        }
        return new ShowcasePreviewLayout.ViewLayout(title, size, dynamicSlots, staticItems, summary, close);
    }

    private boolean isDynamicSymbol(String symbol) {
        return switch (symbol) {
            case "a", "o", "s", "h", "e" -> true;
            default -> false;
        };
    }

    private Map<String, KeyDefinition> parseKeyDefinitions(ConfigurationSection section, LanguageService languageService) {
        Map<String, KeyDefinition> definitions = new LinkedHashMap<>();
        if (section == null) {
            ShowcasePreviewLayout defaults = ShowcasePreviewLayout.defaults();
            definitions.put("#", new KeyDefinition("static", "BLACK_STAINED_GLASS_PANE", " ", List.of()));
            definitions.put("S", new KeyDefinition(
                "summary",
                defaults.inventory().summary().material(),
                defaults.inventory().summary().name(),
                defaults.inventory().summary().lore()
            ));
            definitions.put("X", new KeyDefinition(
                "close",
                defaults.inventory().close().material(),
                defaults.inventory().close().name(),
                defaults.inventory().close().lore()
            ));
            return definitions;
        }

        for (String symbol : section.getKeys(false)) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            ConfigurationSection definition = section.getConfigurationSection(symbol);
            if (definition == null) {
                continue;
            }
            definitions.put(
                symbol.substring(0, 1),
                new KeyDefinition(
                    definition.getString("action", "static"),
                    definition.getString("material", "STONE"),
                    resolveString(languageService, definition.getString("name", " ")),
                    resolveList(languageService, readStringList(definition, "lore"))
                )
            );
        }
        return definitions;
    }

    private Map<String, String> parseTypeDisplays(
        ConfigurationSection section,
        Map<String, String> defaults,
        LanguageService languageService
    ) {
        Map<String, String> displays = new LinkedHashMap<>(defaults);
        if (section == null) {
            return displays;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                displays.put(key.toLowerCase(), resolveString(languageService, value));
            }
        }
        return displays;
    }

    private List<String> normalizeShape(List<String> shape, List<String> fallback) {
        List<String> normalized = new ArrayList<>();
        for (String line : shape) {
            if (line != null && line.length() == 9) {
                normalized.add(line);
            }
        }
        return normalized.isEmpty() ? fallback : List.copyOf(normalized);
    }

    private String resolveString(LanguageService languageService, String value) {
        return languageService == null ? value : languageService.resolveConfigured(value);
    }

    private List<String> resolveList(LanguageService languageService, List<String> values) {
        if (languageService == null) {
            return values == null ? List.of() : List.copyOf(values);
        }
        return languageService.resolveConfiguredList(values);
    }

    private List<String> readStringList(ConfigurationSection section, String path) {
        Object raw = section.get(path);
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object value : list) {
                if (value != null) {
                    result.add(value.toString());
                }
            }
            return List.copyOf(result);
        }
        if (raw instanceof String string) {
            return List.of(string);
        }
        return List.of();
    }

    private record KeyDefinition(
        String action,
        String material,
        String name,
        List<String> lore
    ) {

        private KeyDefinition {
            action = action == null || action.isBlank() ? "static" : action.trim().toLowerCase();
            material = material == null || material.isBlank() ? "STONE" : material;
            name = name == null ? " " : name;
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }
}
