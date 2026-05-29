package ym.ymchat.config.showcase;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import ym.ymchat.service.language.LanguageService;

public final class ItemShowcaseConfigParser {

    private static final String COMMAND_MESSAGE = "lang:item-showcase.command-message";
    private static final String DISABLED_MESSAGE = "lang:item-showcase.messages.disabled";
    private static final String ONLY_PLAYER_MESSAGE = "lang:item-showcase.messages.only-player";
    private static final String ITEM_NO_PERMISSION = "lang:item-showcase.item.messages.no-permission";
    private static final String ITEM_EMPTY_HAND = "lang:item-showcase.item.messages.empty-hand";
    private static final String ITEM_COOLDOWN = "lang:item-showcase.item.messages.cooldown";
    private static final String INVENTORY_NO_PERMISSION = "lang:item-showcase.inventory.messages.no-permission";
    private static final String INVENTORY_EMPTY = "lang:item-showcase.inventory.messages.empty";
    private static final String INVENTORY_COOLDOWN = "lang:item-showcase.inventory.messages.cooldown";
    private static final String ENDER_CHEST_NO_PERMISSION = "lang:item-showcase.ender-chest.messages.no-permission";
    private static final String ENDER_CHEST_EMPTY = "lang:item-showcase.ender-chest.messages.empty";
    private static final String ENDER_CHEST_COOLDOWN = "lang:item-showcase.ender-chest.messages.cooldown";
    private static final String POSITION_NO_PERMISSION = "lang:item-showcase.position.messages.no-permission";
    private static final String POSITION_COOLDOWN = "lang:item-showcase.position.messages.cooldown";

    private final LanguageService languageService;

    public ItemShowcaseConfigParser(LanguageService languageService) {
        this.languageService = languageService;
    }

    public ItemShowcaseSettings parse(FileConfiguration config) {
        ItemShowcaseSettings defaults = ItemShowcaseSettings.defaults();
        return new ItemShowcaseSettings(
            config.getBoolean("Item-Showcase.Enabled", defaults.enabled()),
            localizedConfigString(config, "Item-Showcase.Command-Message", COMMAND_MESSAGE),
            localizedConfigString(config, "Item-Showcase.Messages.Disabled", DISABLED_MESSAGE),
            localizedConfigString(config, "Item-Showcase.Messages.Only-Player", ONLY_PLAYER_MESSAGE),
            parseItemSection(config, defaults.item()),
            parseSnapshotSection(config, "Item-Showcase.Inventory", defaults.inventory(), INVENTORY_NO_PERMISSION,
                INVENTORY_EMPTY, INVENTORY_COOLDOWN),
            parseSnapshotSection(config, "Item-Showcase.Ender-Chest", defaults.enderChest(), ENDER_CHEST_NO_PERMISSION,
                ENDER_CHEST_EMPTY, ENDER_CHEST_COOLDOWN),
            parsePositionSection(config, "Item-Showcase.Position", defaults.position())
        );
    }

    private ItemShowcaseSettings.ItemSection parseItemSection(
        FileConfiguration config,
        ItemShowcaseSettings.ItemSection defaults
    ) {
        List<String> tokens = config.getStringList("Item-Showcase.Item.Tokens");
        if (tokens.isEmpty()) {
            tokens = config.getStringList("Item-Showcase.Tokens");
        }
        if (tokens.isEmpty()) {
            tokens = defaults.tokens();
        }

        String permission = config.getString("Item-Showcase.Item.Permission",
            config.getString("Item-Showcase.Permission", defaults.permission()));
        String bypassPermission = config.getString("Item-Showcase.Item.Bypass-Cooldown-Permission",
            config.getString("Item-Showcase.Bypass-Cooldown-Permission", defaults.bypassCooldownPermission()));
        long cooldown = config.getLong("Item-Showcase.Item.Cooldown-Millis",
            config.getLong("Item-Showcase.Cooldown-Millis", defaults.cooldownMillis()));
        int maxPerMessage = config.getInt("Item-Showcase.Item.Max-Per-Message",
            config.getInt("Item-Showcase.Max-Per-Message", defaults.maxPerMessage()));
        String text = localizedConfigString(config, "Item-Showcase.Item.Item-Text",
            localizedConfigString(config, "Item-Showcase.Item-Text", defaults.text()));
        String noPermission = localizedConfigString(config, "Item-Showcase.Item.Messages.No-Permission",
            localizedConfigString(config, "Item-Showcase.Messages.No-Permission", ITEM_NO_PERMISSION));
        String emptyMessage = localizedConfigString(config, "Item-Showcase.Item.Messages.Empty-Hand",
            localizedConfigString(config, "Item-Showcase.Messages.Empty-Hand", ITEM_EMPTY_HAND));
        String cooldownMessage = localizedConfigString(config, "Item-Showcase.Item.Messages.Cooldown",
            localizedConfigString(config, "Item-Showcase.Messages.Cooldown", ITEM_COOLDOWN));

        return new ItemShowcaseSettings.ItemSection(
            config.getBoolean("Item-Showcase.Item.Enabled", defaults.enabled()),
            permission,
            bypassPermission,
            cooldown,
            tokens,
            maxPerMessage,
            text,
            noPermission,
            emptyMessage,
            cooldownMessage
        );
    }

    private ItemShowcaseSettings.SnapshotSection parseSnapshotSection(
        FileConfiguration config,
        String path,
        ItemShowcaseSettings.SnapshotSection defaults,
        String noPermissionFallback,
        String emptyFallback,
        String cooldownFallback
    ) {
        List<String> tokens = config.getStringList(path + ".Tokens");
        if (tokens.isEmpty()) {
            tokens = defaults.tokens();
        }
        return new ItemShowcaseSettings.SnapshotSection(
            config.getBoolean(path + ".Enabled", defaults.enabled()),
            config.getString(path + ".Permission", defaults.permission()),
            config.getString(path + ".Bypass-Cooldown-Permission", defaults.bypassCooldownPermission()),
            config.getLong(path + ".Cooldown-Millis", defaults.cooldownMillis()),
            tokens,
            config.getInt(path + ".Max-Per-Message", defaults.maxPerMessage()),
            localizedConfigString(config, path + ".Text", defaults.text()),
            localizedConfigStringList(config, path + ".Hover", defaults.hover()),
            localizedConfigString(config, path + ".Messages.No-Permission", noPermissionFallback),
            localizedConfigString(config, path + ".Messages.Empty", emptyFallback),
            localizedConfigString(config, path + ".Messages.Cooldown", cooldownFallback)
        );
    }

    private ItemShowcaseSettings.PositionSection parsePositionSection(
        FileConfiguration config,
        String path,
        ItemShowcaseSettings.PositionSection defaults
    ) {
        List<String> tokens = config.getStringList(path + ".Tokens");
        if (tokens.isEmpty()) {
            tokens = defaults.tokens();
        }
        return new ItemShowcaseSettings.PositionSection(
            config.getBoolean(path + ".Enabled", defaults.enabled()),
            config.getString(path + ".Permission", defaults.permission()),
            config.getString(path + ".Bypass-Cooldown-Permission", defaults.bypassCooldownPermission()),
            config.getLong(path + ".Cooldown-Millis", defaults.cooldownMillis()),
            tokens,
            config.getInt(path + ".Max-Per-Message", defaults.maxPerMessage()),
            localizedConfigString(config, path + ".Text", defaults.text()),
            localizedConfigStringList(config, path + ".Hover", defaults.hover()),
            localizedConfigString(config, path + ".Copy-Text", defaults.copyText()),
            localizedConfigString(config, path + ".Messages.No-Permission", POSITION_NO_PERMISSION),
            localizedConfigString(config, path + ".Messages.Cooldown", POSITION_COOLDOWN)
        );
    }

    private String localizedConfigString(FileConfiguration config, String path, String fallback) {
        return localizedString(config.getString(path, fallback), fallback);
    }

    private List<String> localizedConfigStringList(FileConfiguration config, String path, List<String> fallback) {
        List<String> configured = config.getStringList(path);
        if (configured.isEmpty()) {
            configured = fallback == null ? List.of() : fallback;
        }
        return languageService == null ? configured : languageService.resolveConfiguredList(configured);
    }

    private String localizedString(Object value, String fallback) {
        String raw = value == null ? fallback : value.toString();
        return languageService == null ? raw : languageService.resolveConfigured(raw);
    }
}
