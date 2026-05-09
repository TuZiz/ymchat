package ym.ymchat.service.color;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ColorCodeUtil {

    private static final Pattern RGB = Pattern.compile("(?i)#?[0-9a-f]{6}");
    private static final Pattern AMPERSAND_RGB = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)&[0-9a-f]");

    private ColorCodeUtil() {
    }

    public static String normalizeBaseColorValue(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String value = input.trim();
        if (LEGACY_COLOR.matcher(value).matches()) {
            return "&" + Character.toLowerCase(value.charAt(1));
        }
        if (AMPERSAND_RGB.matcher(value).matches()) {
            return "&#" + value.substring(2).toUpperCase(Locale.ROOT);
        }
        if (RGB.matcher(value).matches()) {
            String normalized = value.startsWith("#") ? value.substring(1) : value;
            return "&#" + normalized.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    public static String normalizeStoredRgb(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim();
        if (!RGB.matcher(value).matches()) {
            return null;
        }
        return "#" + (value.startsWith("#") ? value.substring(1) : value).toUpperCase(Locale.ROOT);
    }

    public static String storedRgbToBaseColor(String input) {
        String normalized = normalizeStoredRgb(input);
        return normalized == null ? null : "&#" + normalized.substring(1);
    }

    public static boolean isLegacyColorCode(char code) {
        return "0123456789abcdefr".indexOf(Character.toLowerCase(code)) >= 0;
    }

    public static boolean isLegacyFormatCode(char code) {
        return "klmno".indexOf(Character.toLowerCase(code)) >= 0;
    }
}
