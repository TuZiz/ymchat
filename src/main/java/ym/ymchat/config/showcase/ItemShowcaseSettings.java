package ym.ymchat.config.showcase;

import java.util.List;

public record ItemShowcaseSettings(
    boolean enabled,
    String commandMessage,
    String disabledMessage,
    String onlyPlayerMessage,
    ItemSection item,
    SnapshotSection inventory,
    SnapshotSection enderChest,
    PositionSection position
) {

    private static final String DEFAULT_COMMAND_MESSAGE = "Use [item] in chat to showcase your held item.";
    private static final String DEFAULT_DISABLED_MESSAGE = "&#777777[&#FF6B6B!&#777777] &#FF6B6BItem showcase is disabled.";
    private static final String DEFAULT_ONLY_PLAYER_MESSAGE = "&#777777[&#FF6B6B!&#777777] &#FF6B6BOnly players can showcase chat content.";
    private static final String DEFAULT_ITEM_TEXT = "&#FFD700[{item_name} &#B0B0B0x{amount}&#FFD700]";
    private static final String DEFAULT_ITEM_NO_PERMISSION = "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou do not have permission to showcase items.";
    private static final String DEFAULT_ITEM_EMPTY_HAND = "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou are not holding an item to showcase.";
    private static final String DEFAULT_ITEM_COOLDOWN = "&#777777[&#FFD700!&#777777] &#FFD700Please wait %remaining_seconds% seconds before showcasing again.";
    private static final String DEFAULT_INVENTORY_TEXT = "&#33CCFF[Inventory]";
    private static final List<String> DEFAULT_INVENTORY_HOVER = List.of(
        "&#B0B0B0Player: &#FFFFFF%player_name%",
        "&#33CCFFClick to view this inventory snapshot"
    );
    private static final String DEFAULT_INVENTORY_NO_PERMISSION = "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou do not have permission to showcase your inventory.";
    private static final String DEFAULT_INVENTORY_EMPTY = "&#777777[&#FF6B6B!&#777777] &#FF6B6BThere is no inventory content to showcase.";
    private static final String DEFAULT_INVENTORY_COOLDOWN = "&#777777[&#FFD700!&#777777] &#FFD700Please wait %remaining_seconds% seconds before showcasing your inventory again.";
    private static final String DEFAULT_ENDER_CHEST_TEXT = "&#D9A7FF[Ender Chest]";
    private static final List<String> DEFAULT_ENDER_CHEST_HOVER = List.of(
        "&#B0B0B0Player: &#FFFFFF%player_name%",
        "&#D9A7FFClick to view this ender chest snapshot"
    );
    private static final String DEFAULT_ENDER_CHEST_NO_PERMISSION = "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou do not have permission to showcase your ender chest.";
    private static final String DEFAULT_ENDER_CHEST_EMPTY = "&#777777[&#FF6B6B!&#777777] &#FF6B6BThere is no ender chest content to showcase.";
    private static final String DEFAULT_ENDER_CHEST_COOLDOWN = "&#777777[&#FFD700!&#777777] &#FFD700Please wait %remaining_seconds% seconds before showcasing your ender chest again.";
    private static final String DEFAULT_POSITION_TEXT = "&#55E690[%world% %x% %y% %z%]";
    private static final List<String> DEFAULT_POSITION_HOVER = List.of(
        "&#B0B0B0Player: &#FFFFFF%player_name%",
        "&#B0B0B0World: &#FFFFFF%world%",
        "&#B0B0B0Position: &#FFFFFF%x%, %y%, %z%",
        "&#FFD700Click to copy coordinates"
    );
    private static final String DEFAULT_POSITION_NO_PERMISSION = "&#777777[&#FF6B6B!&#777777] &#FF6B6BYou do not have permission to showcase coordinates.";
    private static final String DEFAULT_POSITION_COOLDOWN = "&#777777[&#FFD700!&#777777] &#FFD700Please wait %remaining_seconds% seconds before showcasing coordinates again.";

    public ItemShowcaseSettings {
        commandMessage = blankOrDefault(commandMessage, DEFAULT_COMMAND_MESSAGE);
        disabledMessage = blankOrDefault(disabledMessage, DEFAULT_DISABLED_MESSAGE);
        onlyPlayerMessage = blankOrDefault(onlyPlayerMessage, DEFAULT_ONLY_PLAYER_MESSAGE);
        item = item == null ? ItemSection.defaults() : item;
        inventory = inventory == null ? SnapshotSection.inventoryDefaults() : inventory;
        enderChest = enderChest == null ? SnapshotSection.enderChestDefaults() : enderChest;
        position = position == null ? PositionSection.defaults() : position;
    }

    public static ItemShowcaseSettings defaults() {
        return new ItemShowcaseSettings(
            true,
            DEFAULT_COMMAND_MESSAGE,
            DEFAULT_DISABLED_MESSAGE,
            DEFAULT_ONLY_PLAYER_MESSAGE,
            ItemSection.defaults(),
            SnapshotSection.inventoryDefaults(),
            SnapshotSection.enderChestDefaults(),
            PositionSection.defaults()
        );
    }

    public String permission() {
        return item.permission();
    }

    public String bypassCooldownPermission() {
        return item.bypassCooldownPermission();
    }

    public long cooldownMillis() {
        return item.cooldownMillis();
    }

    public List<String> tokens() {
        return item.tokens();
    }

    public int maxPerMessage() {
        return item.maxPerMessage();
    }

    public String itemText() {
        return item.text();
    }

    public String noPermissionMessage() {
        return item.noPermissionMessage();
    }

    public String emptyHandMessage() {
        return item.emptyMessage();
    }

    public String cooldownMessage() {
        return item.cooldownMessage();
    }

    public record ItemSection(
        boolean enabled,
        String permission,
        String bypassCooldownPermission,
        long cooldownMillis,
        List<String> tokens,
        int maxPerMessage,
        String text,
        String noPermissionMessage,
        String emptyMessage,
        String cooldownMessage
    ) {

        public ItemSection {
            permission = blankOrDefault(permission, "ymchat.item.showcase");
            bypassCooldownPermission = blankOrDefault(bypassCooldownPermission, "ymchat.item.showcase.bypass");
            tokens = normalizeTokens(tokens, List.of("[item]", "[i]", ":i:"));
            maxPerMessage = Math.max(1, maxPerMessage);
            text = blankOrDefault(text, DEFAULT_ITEM_TEXT);
            noPermissionMessage = blankOrDefault(noPermissionMessage, DEFAULT_ITEM_NO_PERMISSION);
            emptyMessage = blankOrDefault(emptyMessage, DEFAULT_ITEM_EMPTY_HAND);
            cooldownMessage = blankOrDefault(cooldownMessage, DEFAULT_ITEM_COOLDOWN);
        }

        public static ItemSection defaults() {
            return new ItemSection(
                true,
                "ymchat.item.showcase",
                "ymchat.item.showcase.bypass",
                3000L,
                List.of("[item]", "[i]", ":i:"),
                1,
                DEFAULT_ITEM_TEXT,
                DEFAULT_ITEM_NO_PERMISSION,
                DEFAULT_ITEM_EMPTY_HAND,
                DEFAULT_ITEM_COOLDOWN
            );
        }
    }

    public record SnapshotSection(
        boolean enabled,
        String permission,
        String bypassCooldownPermission,
        long cooldownMillis,
        List<String> tokens,
        int maxPerMessage,
        String text,
        List<String> hover,
        String noPermissionMessage,
        String emptyMessage,
        String cooldownMessage
    ) {

        public SnapshotSection {
            tokens = normalizeTokens(tokens, List.of());
            maxPerMessage = Math.max(1, maxPerMessage);
            text = blankOrDefault(text, DEFAULT_INVENTORY_TEXT);
            hover = hover == null ? List.of() : List.copyOf(hover);
            noPermissionMessage = blankOrDefault(noPermissionMessage, DEFAULT_INVENTORY_NO_PERMISSION);
            emptyMessage = blankOrDefault(emptyMessage, DEFAULT_INVENTORY_EMPTY);
            cooldownMessage = blankOrDefault(cooldownMessage, DEFAULT_INVENTORY_COOLDOWN);
        }

        public static SnapshotSection inventoryDefaults() {
            return new SnapshotSection(
                true,
                "ymchat.showcase.inventory",
                "ymchat.showcase.inventory.bypass",
                5000L,
                List.of("[inventory]", "[inv]"),
                1,
                DEFAULT_INVENTORY_TEXT,
                DEFAULT_INVENTORY_HOVER,
                DEFAULT_INVENTORY_NO_PERMISSION,
                DEFAULT_INVENTORY_EMPTY,
                DEFAULT_INVENTORY_COOLDOWN
            );
        }

        public static SnapshotSection enderChestDefaults() {
            return new SnapshotSection(
                true,
                "ymchat.showcase.enderchest",
                "ymchat.showcase.enderchest.bypass",
                5000L,
                List.of("[enderchest]", "[ec]"),
                1,
                DEFAULT_ENDER_CHEST_TEXT,
                DEFAULT_ENDER_CHEST_HOVER,
                DEFAULT_ENDER_CHEST_NO_PERMISSION,
                DEFAULT_ENDER_CHEST_EMPTY,
                DEFAULT_ENDER_CHEST_COOLDOWN
            );
        }
    }

    public record PositionSection(
        boolean enabled,
        String permission,
        String bypassCooldownPermission,
        long cooldownMillis,
        List<String> tokens,
        int maxPerMessage,
        String text,
        List<String> hover,
        String copyText,
        String noPermissionMessage,
        String cooldownMessage
    ) {

        public PositionSection {
            permission = blankOrDefault(permission, "ymchat.showcase.position");
            bypassCooldownPermission = blankOrDefault(bypassCooldownPermission, "ymchat.showcase.position.bypass");
            tokens = normalizeTokens(tokens, List.of("[position]", "[pos]"));
            maxPerMessage = Math.max(1, maxPerMessage);
            text = blankOrDefault(text, DEFAULT_POSITION_TEXT);
            hover = hover == null ? List.of() : List.copyOf(hover);
            copyText = blankOrDefault(copyText, "%world% %x% %y% %z%");
            noPermissionMessage = blankOrDefault(noPermissionMessage, DEFAULT_POSITION_NO_PERMISSION);
            cooldownMessage = blankOrDefault(cooldownMessage, DEFAULT_POSITION_COOLDOWN);
        }

        public static PositionSection defaults() {
            return new PositionSection(
                true,
                "ymchat.showcase.position",
                "ymchat.showcase.position.bypass",
                1000L,
                List.of("[position]", "[pos]"),
                1,
                DEFAULT_POSITION_TEXT,
                DEFAULT_POSITION_HOVER,
                "%world% %x% %y% %z%",
                DEFAULT_POSITION_NO_PERMISSION,
                DEFAULT_POSITION_COOLDOWN
            );
        }
    }

    private static List<String> normalizeTokens(List<String> tokens, List<String> fallback) {
        if (tokens == null || tokens.isEmpty()) {
            return List.copyOf(fallback);
        }
        return tokens.stream()
            .filter(token -> token != null && !token.isBlank())
            .toList();
    }

    private static String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}