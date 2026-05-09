package ym.ymchat.config.megaphone;

import java.util.Locale;

public enum MegaphoneMode {
    CHAT("Chat"),
    TITLE("Title"),
    BOSSBAR("BossBar");

    private final String configKey;

    MegaphoneMode(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static MegaphoneMode parse(String input) {
        String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "chat", "\u804a\u5929\u680f", "\u804a\u5929", "\u6587\u5b57" -> CHAT;
            case "title", "\u6807\u9898", "\u5c4f\u5e55", "\u5927\u6807\u9898" -> TITLE;
            case "bossbar", "boss", "bar", "\u9876\u90e8\u680f", "\u8840\u6761", "\u9996\u9886\u680f" -> BOSSBAR;
            default -> null;
        };
    }
}
