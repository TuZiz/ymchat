package ym.ymchat.config.megaphone;

import org.bukkit.configuration.file.FileConfiguration;
import ym.ymchat.service.language.LanguageService;

public final class MegaphoneConfigParser {

    private final LanguageService languageService;

    public MegaphoneConfigParser(LanguageService languageService) {
        this.languageService = languageService;
    }

    public MegaphoneSettings parse(FileConfiguration config) {
        MegaphoneSettings defaults = MegaphoneSettings.defaults();
        MegaphoneMode defaultMode = MegaphoneMode.parse(config.getString("Megaphone.Default-Mode", defaults.defaultMode().name()));
        java.util.EnumMap<MegaphoneMode, MegaphoneSettings.ModeSettings> modes = new java.util.EnumMap<>(MegaphoneMode.class);
        for (MegaphoneMode mode : MegaphoneMode.values()) {
            modes.put(mode, parseMode(config, "Megaphone.Modes." + mode.configKey(), defaults.mode(mode)));
        }
        return new MegaphoneSettings(
            config.getBoolean("Megaphone.Enabled", defaults.enabled()),
            config.getString("Megaphone.Data-File", defaults.dataFile()),
            config.getString("Megaphone.Use-Permission", defaults.usePermission()),
            config.getString("Megaphone.Admin-Permission", defaults.adminPermission()),
            defaultMode == null ? defaults.defaultMode() : defaultMode,
            config.getBoolean("Megaphone.Capture-World-Channel", defaults.captureWorldChannel()),
            modes
        );
    }

    private MegaphoneSettings.ModeSettings parseMode(
        FileConfiguration config,
        String path,
        MegaphoneSettings.ModeSettings defaults
    ) {
        return new MegaphoneSettings.ModeSettings(
            config.getBoolean(path + ".Enabled", defaults.enabled()),
            config.getInt(path + ".Cost", defaults.cost()),
            config.getString(path + ".Permission", defaults.permission()),
            localizedConfigString(config, path + ".Display", defaults.display()),
            localizedConfigString(config, path + ".Format", defaults.chatFormat()),
            localizedConfigString(config, path + ".Title", defaults.title()),
            localizedConfigString(config, path + ".Subtitle", defaults.subtitle()),
            config.getInt(path + ".Fade-In-Ticks", defaults.fadeInTicks()),
            config.getInt(path + ".Stay-Ticks", defaults.stayTicks()),
            config.getInt(path + ".Fade-Out-Ticks", defaults.fadeOutTicks()),
            localizedConfigString(config, path + ".Text", defaults.bossBarText()),
            config.getString(path + ".Color", defaults.bossBarColor()),
            config.getString(path + ".Style", defaults.bossBarStyle()),
            config.getInt(path + ".Duration-Ticks", defaults.bossBarDurationTicks()),
            config.getDouble(path + ".Progress", defaults.bossBarProgress()),
            config.getBoolean(path + ".Animation.Enabled", defaults.bossBarAnimationEnabled()),
            config.getString(path + ".Animation.Style", defaults.bossBarAnimationStyle()),
            config.getInt(path + ".Animation.Interval-Ticks", defaults.bossBarAnimationIntervalTicks()),
            config.getInt(path + ".Animation.Flow-Width", defaults.bossBarAnimationFlowWidth())
        );
    }

    private String localizedConfigString(FileConfiguration config, String path, String fallback) {
        String raw = config.getString(path, fallback);
        return languageService == null ? raw : languageService.resolveConfigured(raw);
    }
}
