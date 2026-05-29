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

    public static final String USE_PERMISSION = ColorScope.CHAT.usePermission();
    public static final String NAME_USE_PERMISSION = ColorScope.NAME.usePermission();

    private final PlayerColorPreferenceRepository repository;

    public PlayerColorService(PlayerColorPreferenceRepository repository) {
        this.repository = repository;
    }

    public ResolvedColor resolve(Player player, ColorChatSettings settings, String ruleDefaultColor) {
        return resolve(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings(), ruleDefaultColor);
    }

    public ResolvedColor resolve(Player player, ColorScope scope, FixedColorSettings settings, String ruleDefaultColor) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        return resolve(
            player.getUniqueId(),
            permission -> hasRuntimePermission(player, permission),
            effectiveScope,
            settings,
            ruleDefaultColor
        );
    }

    public ResolvedColor resolve(
        UUID playerId,
        Predicate<String> permissionChecker,
        ColorChatSettings settings,
        String ruleDefaultColor
    ) {
        return resolve(
            playerId,
            permissionChecker,
            ColorScope.CHAT,
            settings == null ? null : settings.fixedSettings(),
            ruleDefaultColor
        );
    }

    public ResolvedColor resolve(
        UUID playerId,
        Predicate<String> permissionChecker,
        ColorScope scope,
        FixedColorSettings settings,
        String ruleDefaultColor
    ) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        String fallbackColor = firstValidColor(ruleDefaultColor, "&f");
        PlayerColorPreference storedPreference = repository.get(playerId, effectiveScope);
        if (settings == null || !settings.enabled()) {
            return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, storedPreference, null);
        }

        PlayerColorPreference preference = mapLegacyPreset(storedPreference, settings);
        if (preference != null) {
            switch (preference.mode()) {
                case OFF -> {
                    return new ResolvedColor(fallbackColor, ColorSource.MANUAL_OFF, preference, null);
                }
                case LEGACY -> {
                    String code = normalizeLegacyCode(preference.value());
                    if (code != null && hasPermission(permissionChecker, legacyPermission(effectiveScope, code))) {
                        return new ResolvedColor("&" + code, ColorSource.MANUAL_LEGACY, preference, null);
                    }
                    return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, preference, null);
                }
                case RGB -> {
                    ColorPreset rgbColor = settings.findRgbColor(preference.value());
                    if (rgbColor != null && hasPermission(permissionChecker, rgbColor.permission())) {
                        String color = firstValidColor(rgbColor.value(), fallbackColor);
                        return new ResolvedColor(color, ColorSource.MANUAL_RGB, preference, rgbColor);
                    }
                    return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, preference, null);
                }
                case PRESET -> {
                    return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, preference, null);
                }
            }
        }

        return new ResolvedColor(fallbackColor, ColorSource.RULE_DEFAULT, null, null);
    }

    public List<String> availableLegacyCodes(Player player, ColorChatSettings settings) {
        return availableLegacyCodes(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings());
    }

    public List<String> availableLegacyCodes(Player player, ColorScope scope, FixedColorSettings settings) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        if (!canUseCommands(player, effectiveScope, settings)) {
            return List.of();
        }
        return LEGACY_CODES.stream()
            .filter(code -> hasRuntimePermission(player, legacyPermission(effectiveScope, code)))
            .toList();
    }

    public List<ColorPreset> availableRgbColors(Player player, ColorChatSettings settings) {
        return availableRgbColors(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings());
    }

    public List<ColorPreset> availableRgbColors(Player player, ColorScope scope, FixedColorSettings settings) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        if (!canUseCommands(player, effectiveScope, settings)) {
            return List.of();
        }
        return settings.rgbColors().stream()
            .filter(color -> hasRuntimePermission(player, color.permission()))
            .toList();
    }

    public boolean canUseCommands(Player player, ColorChatSettings settings) {
        return canUseCommands(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings());
    }

    public boolean canUseCommands(Player player, ColorScope scope, FixedColorSettings settings) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        return settings != null
            && settings.enabled()
            && hasRuntimePermission(player, effectiveScope.usePermission());
    }

    public boolean setLegacy(Player player, ColorChatSettings settings, String code) {
        return setLegacy(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings(), code);
    }

    public boolean setLegacy(Player player, ColorScope scope, FixedColorSettings settings, String code) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        if (!canUseCommands(player, effectiveScope, settings)) {
            return false;
        }
        String normalized = normalizeLegacyCode(code);
        if (normalized == null || !hasRuntimePermission(player, legacyPermission(effectiveScope, normalized))) {
            return false;
        }
        repository.save(player.getUniqueId(), effectiveScope, PlayerColorPreference.legacy(normalized));
        return true;
    }

    public boolean setRgb(Player player, ColorChatSettings settings, String rgbId) {
        return setRgb(player, ColorScope.CHAT, settings == null ? null : settings.fixedSettings(), rgbId);
    }

    public boolean setRgb(Player player, ColorScope scope, FixedColorSettings settings, String rgbId) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        if (!canUseCommands(player, effectiveScope, settings)) {
            return false;
        }
        ColorPreset rgbColor = settings.findRgbColor(rgbId);
        if (rgbColor == null || !hasRuntimePermission(player, rgbColor.permission())) {
            return false;
        }
        repository.save(player.getUniqueId(), effectiveScope, PlayerColorPreference.rgb(rgbColor.id()));
        return true;
    }

    public void setOff(Player player) {
        setOff(player, ColorScope.CHAT);
    }

    public void setOff(Player player, ColorScope scope) {
        repository.save(player.getUniqueId(), scope == null ? ColorScope.CHAT : scope, PlayerColorPreference.off());
    }

    public void reset(Player player) {
        reset(player, ColorScope.CHAT);
    }

    public void reset(Player player, ColorScope scope) {
        repository.remove(player.getUniqueId(), scope == null ? ColorScope.CHAT : scope);
    }

    public String currentStoredValue(Player player) {
        return currentStoredValue(player, ColorScope.CHAT);
    }

    public String currentStoredValue(Player player, ColorScope scope) {
        PlayerColorPreference preference = repository.get(player.getUniqueId(), scope == null ? ColorScope.CHAT : scope);
        return preference == null ? "" : preference.value();
    }

    public static String legacyPermission(String code) {
        return legacyPermission(ColorScope.CHAT, code);
    }

    public static String legacyPermission(ColorScope scope, String code) {
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        return effectiveScope.legacyPermission(code);
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

    private PlayerColorPreference mapLegacyPreset(PlayerColorPreference preference, FixedColorSettings settings) {
        if (preference == null || preference.mode() != PlayerColorPreference.Mode.PRESET) {
            return preference;
        }

        return switch (preference.value().toLowerCase(Locale.ROOT)) {
            case "yellow" -> PlayerColorPreference.legacy("e");
            case "green" -> PlayerColorPreference.legacy("a");
            case "aqua" -> PlayerColorPreference.legacy("b");
            case "gold" -> PlayerColorPreference.legacy("6");
            case "pink" -> settings.findRgbColor("pink") == null ? PlayerColorPreference.off() : PlayerColorPreference.rgb("pink");
            default -> PlayerColorPreference.off();
        };
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
        String lowered = permission.toLowerCase(Locale.ROOT);
        return player.isOp() && (lowered.startsWith("ymchat.color.") || lowered.startsWith("ymchat.namecolor."));
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
