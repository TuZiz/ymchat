package ym.ymchat.service.color;

import java.util.Locale;

public enum ColorScope {
    CHAT("chat", "ymchat.color.use", "ymchat.color"),
    NAME("name", "ymchat.namecolor.use", "ymchat.namecolor");

    private final String storageKey;
    private final String usePermission;
    private final String permissionPrefix;

    ColorScope(String storageKey, String usePermission, String permissionPrefix) {
        this.storageKey = storageKey;
        this.usePermission = usePermission;
        this.permissionPrefix = permissionPrefix;
    }

    public String storageKey() {
        return storageKey;
    }

    public String usePermission() {
        return usePermission;
    }

    public String permissionPrefix() {
        return permissionPrefix;
    }

    public String legacyPermission(String code) {
        return permissionPrefix + "." + String.valueOf(code).toLowerCase(Locale.ROOT);
    }

    public String rgbPermission(String id) {
        return permissionPrefix + ".rgb." + String.valueOf(id).toLowerCase(Locale.ROOT);
    }

    public static ColorScope fromStorageKey(String input) {
        if (input == null || input.isBlank()) {
            return CHAT;
        }
        for (ColorScope scope : values()) {
            if (scope.storageKey.equalsIgnoreCase(input) || scope.name().equalsIgnoreCase(input)) {
                return scope;
            }
        }
        return CHAT;
    }
}
