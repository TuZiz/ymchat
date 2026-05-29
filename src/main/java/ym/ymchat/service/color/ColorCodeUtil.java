package ym.ymchat.service.color;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class ColorCodeUtil {

    private static final Pattern RGB = Pattern.compile("(?i)#?[0-9a-f]{6}");
    private static final Pattern AMPERSAND_RGB = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)&[0-9a-f]");
    private static final Map<Character, String> LEGACY_RGB = Map.ofEntries(
        Map.entry('0', "000000"),
        Map.entry('1', "0000AA"),
        Map.entry('2', "00AA00"),
        Map.entry('3', "00AAAA"),
        Map.entry('4', "AA0000"),
        Map.entry('5', "AA00AA"),
        Map.entry('6', "FFAA00"),
        Map.entry('7', "AAAAAA"),
        Map.entry('8', "555555"),
        Map.entry('9', "5555FF"),
        Map.entry('a', "55FF55"),
        Map.entry('b', "55FFFF"),
        Map.entry('c', "FF5555"),
        Map.entry('d', "FF55FF"),
        Map.entry('e', "FFFF55"),
        Map.entry('f', "FFFFFF")
    );

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

    public static String normalizeGradientColorValue(String input) {
        String normalized = normalizeBaseColorValue(input);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("&#") && normalized.length() == 8) {
            return normalized;
        }
        if (normalized.startsWith("&") && normalized.length() == 2) {
            String hex = LEGACY_RGB.get(Character.toLowerCase(normalized.charAt(1)));
            return hex == null ? null : "&#" + hex;
        }
        return null;
    }

    public static boolean isLegacyColorCode(char code) {
        return "0123456789abcdefr".indexOf(Character.toLowerCase(code)) >= 0;
    }

    public static boolean isLegacyFormatCode(char code) {
        return "klmno".indexOf(Character.toLowerCase(code)) >= 0;
    }
}
