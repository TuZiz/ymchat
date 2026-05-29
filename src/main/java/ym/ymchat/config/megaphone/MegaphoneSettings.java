package ym.ymchat.config.megaphone;

import java.util.EnumMap;
import java.util.Map;

public record MegaphoneSettings(
    boolean enabled,
    String dataFile,
    String usePermission,
    String adminPermission,
    MegaphoneMode defaultMode,
    boolean captureWorldChannel,
    Map<MegaphoneMode, ModeSettings> modes
) {

    public MegaphoneSettings {
        dataFile = blankOrDefault(dataFile, "megaphones.yml");
        usePermission = blankOrDefault(usePermission, "ymchat.megaphone.use");
        adminPermission = blankOrDefault(adminPermission, "ymchat.megaphone.admin");
        defaultMode = defaultMode == null ? MegaphoneMode.CHAT : defaultMode;
        EnumMap<MegaphoneMode, ModeSettings> normalized = new EnumMap<>(MegaphoneMode.class);
        normalized.putAll(defaultModes());
        if (modes != null) {
            normalized.putAll(modes);
        }
        modes = Map.copyOf(normalized);
    }

    public ModeSettings mode(MegaphoneMode mode) {
        return modes.getOrDefault(mode, defaultModes().get(mode));
    }

    public static MegaphoneSettings defaults() {
        return new MegaphoneSettings(
            true,
            "megaphones.yml",
            "ymchat.megaphone.use",
            "ymchat.megaphone.admin",
            MegaphoneMode.CHAT,
            true,
            defaultModes()
        );
    }

    private static EnumMap<MegaphoneMode, ModeSettings> defaultModes() {
        EnumMap<MegaphoneMode, ModeSettings> defaults = new EnumMap<>(MegaphoneMode.class);
        defaults.put(MegaphoneMode.CHAT, new ModeSettings(
            true,
            1,
            "ymchat.megaphone.chat",
            "Chat",
            "&#777777[&#FFB833Megaphone&#777777] %luckperms_prefix%{name_color}%player_name%{name_reset}&#777777: &#FFFFFF%message%",
            "",
            "",
            10,
            60,
            10,
            "",
            "YELLOW",
            "SOLID",
            100,
            1.0D,
            false,
            "none",
            2,
            18
        ));
        defaults.put(MegaphoneMode.TITLE, new ModeSettings(
            true,
            3,
            "ymchat.megaphone.title",
            "Title",
            "",
            "&#FFB833Megaphone",
            "%luckperms_prefix%{name_color}%player_name%{name_reset}&#777777: &#FFFFFF%message%",
            10,
            60,
            10,
            "",
            "YELLOW",
            "SOLID",
            100,
            1.0D,
            false,
            "none",
            2,
            18
        ));
        defaults.put(MegaphoneMode.BOSSBAR, new ModeSettings(
            true,
            5,
            "ymchat.megaphone.bossbar",
            "BossBar",
            "",
            "",
            "",
            10,
            60,
            10,
            "&#FFB833Megaphone&#777777 | %luckperms_prefix%{name_color}%player_name%{name_reset}&#777777: &#FFFFFF%message%",
            "YELLOW",
            "SOLID",
            100,
            1.0D,
            true,
            "typing-flow",
            2,
            18
        ));
        return defaults;
    }

    public record ModeSettings(
        boolean enabled,
        int cost,
        String permission,
        String display,
        String chatFormat,
        String title,
        String subtitle,
        int fadeInTicks,
        int stayTicks,
        int fadeOutTicks,
        String bossBarText,
        String bossBarColor,
        String bossBarStyle,
        int bossBarDurationTicks,
        double bossBarProgress,
        boolean bossBarAnimationEnabled,
        String bossBarAnimationStyle,
        int bossBarAnimationIntervalTicks,
        int bossBarAnimationFlowWidth
    ) {

        public ModeSettings {
            cost = Math.max(0, cost);
            permission = permission == null ? "" : permission;
            display = blankOrDefault(display, "Chat");
            chatFormat = chatFormat == null ? "" : chatFormat;
            title = title == null ? "" : title;
            subtitle = subtitle == null ? "" : subtitle;
            fadeInTicks = Math.max(0, fadeInTicks);
            stayTicks = Math.max(1, stayTicks);
            fadeOutTicks = Math.max(0, fadeOutTicks);
            bossBarText = bossBarText == null ? "" : bossBarText;
            bossBarColor = blankOrDefault(bossBarColor, "YELLOW");
            bossBarStyle = blankOrDefault(bossBarStyle, "SOLID");
            bossBarDurationTicks = Math.max(20, bossBarDurationTicks);
            bossBarProgress = Math.max(0.0D, Math.min(1.0D, bossBarProgress));
            bossBarAnimationStyle = blankOrDefault(bossBarAnimationStyle, "none");
            bossBarAnimationIntervalTicks = Math.max(1, bossBarAnimationIntervalTicks);
            bossBarAnimationFlowWidth = Math.max(0, bossBarAnimationFlowWidth);
        }
    }

    private static String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
