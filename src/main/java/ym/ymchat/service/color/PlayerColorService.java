package ym.ymchat.service.color;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import ym.ymchat.config.color.ColorChatSettings;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.config.color.FixedColorSettings;

public final class PlayerColorService {

    public static final String USE_PERMISSION = "ymchat.color.use";

    private final PlayerColorPreferenceRepository repository;

    public PlayerColorService(PlayerColorPreferenceRepository repository) {
        this.repository = repository;
    }

    public ResolvedColor resolve(Player player, ColorChatSettings settings, String ruleDefaultColor) {
        return resolve(player.getUniqueId(), permission -> hasRuntimePermission(player, permission), settings, ruleDefaultColor);
    }

    public ResolvedColor resolve(UUID playerId, Predicate<String> permissionChecker, ColorChatSettings settings, String ruleDefaultColor) {
        String fallbackColor = firstValidColor(ruleDefaultColor, "&f");
        if (settings == null) {
            return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, null, null);
        }

        FixedColorSettings fixedSettings = settings.fixedSettings();
        if (fixedSettings == null || !fixedSettings.enabled()) {
            return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, repository.get(playerId), null);
        }

        PlayerColorPreference preference = migrateLegacyPreset(playerId, repository.get(playerId), fixedSettings);
        if (preference != null) {
            switch (preference.mode()) {
                case OFF -> {
                    return new ResolvedColor(fallbackColor, ColorSource.MANUAL_OFF, preference, null);
                }
                case LEGACY -> {
                    String code = normalizeLegacyCode(preference.value());
                    if (code != null && hasPermission(permissionChecker, legacyPermission(code))) {
                        return new ResolvedColor("&" + code, ColorSource.MANUAL_LEGACY, preference, null);
                    }
                    repository.remove(playerId);
                }
                case RGB -> {
                    ColorPreset rgbColor = fixedSettings.findRgbColor(preference.value());
                    if (rgbColor != null && hasPermission(permissionChecker, rgbColor.permission())) {
                        String color = firstValidColor(rgbColor.value(), fallbackColor);
                        return new ResolvedColor(color, ColorSource.MANUAL_RGB, preference, rgbColor);
                    }
                    repository.remove(playerId);
                }
                case PRESET -> {
                    repository.remove(playerId);
                }
            }
        }

        return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, null, null);
    }

    public List<String> availableLegacyCodes(Player player, ColorChatSettings settings) {
        if (!canUseCommands(player, settings)) {
            return List.of();
        }
        return LEGACY_CODES.stream()
            .filter(code -> hasRuntimePermission(player, legacyPermission(code)))
            .toList();
    }

    public List<ColorPreset> availableRgbColors(Player player, ColorChatSettings settings) {
        if (!canUseCommands(player, settings) || settings == null || settings.fixedSettings() == null) {
            return List.of();
        }
        return settings.fixedSettings().rgbColors().stream()
            .filter(color -> hasRuntimePermission(player, color.permission()))
            .toList();
    }

    public boolean canUseCommands(Player player, ColorChatSettings settings) {
        FixedColorSettings fixedSettings = settings == null ? null : settings.fixedSettings();
        return fixedSettings != null
            && fixedSettings.enabled()
            && hasRuntimePermission(player, USE_PERMISSION);
    }

    public boolean setLegacy(Player player, ColorChatSettings settings, String code) {
        if (!canUseCommands(player, settings)) {
            return false;
        }
        String normalized = normalizeLegacyCode(code);
        if (normalized == null || !hasRuntimePermission(player, legacyPermission(normalized))) {
            return false;
        }
        repository.save(player.getUniqueId(), PlayerColorPreference.legacy(normalized));
        return true;
    }

    public boolean setRgb(Player player, ColorChatSettings settings, String rgbId) {
        if (!canUseCommands(player, settings) || settings == null || settings.fixedSettings() == null) {
            return false;
        }
        ColorPreset rgbColor = settings.fixedSettings().findRgbColor(rgbId);
        if (rgbColor == null || !hasRuntimePermission(player, rgbColor.permission())) {
            return false;
        }
        repository.save(player.getUniqueId(), PlayerColorPreference.rgb(rgbColor.id()));
        return true;
    }

    public void setOff(Player player) {
        repository.save(player.getUniqueId(), PlayerColorPreference.off());
    }

    public void reset(Player player) {
        repository.remove(player.getUniqueId());
    }

    public String currentStoredValue(Player player) {
        PlayerColorPreference preference = repository.get(player.getUniqueId());
        return preference == null ? "" : preference.value();
    }

    public static String legacyPermission(String code) {
        return "ymchat.color." + code.toLowerCase(Locale.ROOT);
    }

    public static String normalizeLegacyCode(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("&") && value.length() == 2) {
            value = value.substring(1);
        }
        return LEGACY_CODES.contains(value) ? value : null;
    }

    private PlayerColorPreference migrateLegacyPreset(UUID playerId, PlayerColorPreference preference, FixedColorSettings settings) {
        if (preference == null || preference.mode() != PlayerColorPreference.Mode.PRESET) {
            return preference;
        }

        PlayerColorPreference migrated = switch (preference.value().toLowerCase(Locale.ROOT)) {
            case "yellow" -> PlayerColorPreference.legacy("e");
            case "green" -> PlayerColorPreference.legacy("a");
            case "aqua" -> PlayerColorPreference.legacy("b");
            case "gold" -> PlayerColorPreference.legacy("6");
            case "pink" -> settings.findRgbColor("pink") == null ? PlayerColorPreference.off() : PlayerColorPreference.rgb("pink");
            default -> PlayerColorPreference.off();
        };

        if (migrated.mode() == PlayerColorPreference.Mode.OFF) {
            repository.remove(playerId);
            return null;
        }
        repository.save(playerId, migrated);
        return migrated;
    }

    private boolean hasPermission(Predicate<String> permissionChecker, String permission) {
        return permission == null || permission.isBlank() || permissionChecker.test(permission);
    }

    private boolean hasRuntimePermission(Player player, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (player.hasPermission(permission)) {
            return true;
        }
        return player.isOp() && permission.toLowerCase(Locale.ROOT).startsWith("ymchat.color.");
    }

    private String firstValidColor(String primary, String fallback) {
        String normalizedPrimary = ColorCodeUtil.normalizeBaseColorValue(primary);
        if (normalizedPrimary != null) {
            return normalizedPrimary;
        }
        String normalizedFallback = ColorCodeUtil.normalizeBaseColorValue(fallback);
        return normalizedFallback == null ? "&f" : normalizedFallback;
    }

    private static final List<String> LEGACY_CODES = List.of(
        "0", "1", "2", "3", "4", "5", "6", "7",
        "8", "9", "a", "b", "c", "d", "e", "f"
    );

    public enum ColorSource {
        MANUAL_LEGACY,
        MANUAL_RGB,
        MANUAL_OFF,
        RULE_DEFAULT
    }

    public record ResolvedColor(
        String baseColorValue,
        ColorSource source,
        PlayerColorPreference preference,
        ColorPreset rgbColor
    ) {
    }
}
