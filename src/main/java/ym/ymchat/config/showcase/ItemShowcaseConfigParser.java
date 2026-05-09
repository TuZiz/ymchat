package ym.ymchat.config.showcase;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import ym.ymchat.service.language.LanguageService;

public final class ItemShowcaseConfigParser {

    private final LanguageService languageService;

    public ItemShowcaseConfigParser(LanguageService languageService) {
        this.languageService = languageService;
    }

    public ItemShowcaseSettings parse(FileConfiguration config) {
        ItemShowcaseSettings defaults = ItemShowcaseSettings.defaults();
        return new ItemShowcaseSettings(
            config.getBoolean("Item-Showcase.Enabled", defaults.enabled()),
            localizedConfigString(config, "Item-Showcase.Command-Message", defaults.commandMessage()),
            localizedConfigString(config, "Item-Showcase.Messages.Disabled", defaults.disabledMessage()),
            localizedConfigString(config, "Item-Showcase.Messages.Only-Player", defaults.onlyPlayerMessage()),
            parseItemSection(config, defaults.item()),
            parseSnapshotSection(config, "Item-Showcase.Inventory", defaults.inventory()),
            parseSnapshotSection(config, "Item-Showcase.Ender-Chest", defaults.enderChest()),
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
            localizedConfigString(config, "Item-Showcase.Messages.No-Permission", defaults.noPermissionMessage()));
        String emptyMessage = localizedConfigString(config, "Item-Showcase.Item.Messages.Empty-Hand",
            localizedConfigString(config, "Item-Showcase.Messages.Empty-Hand", defaults.emptyMessage()));
        String cooldownMessage = localizedConfigString(config, "Item-Showcase.Item.Messages.Cooldown",
            localizedConfigString(config, "Item-Showcase.Messages.Cooldown", defaults.cooldownMessage()));

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
        ItemShowcaseSettings.SnapshotSection defaults
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
            localizedConfigString(config, path + ".Messages.No-Permission", defaults.noPermissionMessage()),
            localizedConfigString(config, path + ".Messages.Empty", defaults.emptyMessage()),
            localizedConfigString(config, path + ".Messages.Cooldown", defaults.cooldownMessage())
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
            localizedConfigString(config, path + ".Messages.No-Permission", defaults.noPermissionMessage()),
            localizedConfigString(config, path + ".Messages.Cooldown", defaults.cooldownMessage())
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
