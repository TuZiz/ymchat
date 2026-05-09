package ym.ymchat.service.megaphone;

import java.util.Locale;

final class BossBarTextAnimator {

    private static final char COLOR_CHAR = '\u00A7';

    private BossBarTextAnimator() {
    }

    static String frame(String legacyText, String style, int step, int flowWidth) {
        String text = legacyText == null ? "" : legacyText;
        if (text.isBlank()) {
            return text;
        }
        String normalizedStyle = style == null ? "none" : style.trim().toLowerCase(Locale.ROOT);
        int safeStep = Math.max(1, step);
        int width = Math.max(1, flowWidth);
        return switch (normalizedStyle) {
            case "typing", "typewriter" -> typewriter(text, safeStep);
            case "flow", "marquee" -> flow(text, safeStep, width);
            case "typing-flow", "typewriter-flow", "typingflow" -> typingFlow(text, safeStep, width);
            default -> text;
        };
    }

    private static String typingFlow(String legacyText, int step, int flowWidth) {
        int visibleLength = visibleLength(legacyText);
        if (visibleLength <= 0 || step <= visibleLength) {
            return typewriter(legacyText, step);
        }
        return flow(legacyText, step - visibleLength, flowWidth);
    }

    private static String typewriter(String legacyText, int visibleLimit) {
        int safeLimit = Math.max(1, visibleLimit);
        StringBuilder builder = new StringBuilder();
        int visible = 0;
        for (int index = 0; index < legacyText.length(); index++) {
            char current = legacyText.charAt(index);
            if (current == COLOR_CHAR && index + 1 < legacyText.length()) {
                builder.append(current).append(legacyText.charAt(++index));
                continue;
            }
            if (visible >= safeLimit) {
                break;
            }
            builder.append(current);
            visible++;
        }
        return builder.toString();
    }

    private static String flow(String legacyText, int step, int flowWidth) {
        int offset = Math.floorMod(step - 1, flowWidth + 1);
        if (offset == 0) {
            return legacyText;
        }
        return " ".repeat(offset) + legacyText;
    }

    private static int visibleLength(String legacyText) {
        int visible = 0;
        for (int index = 0; index < legacyText.length(); index++) {
            if (legacyText.charAt(index) == COLOR_CHAR && index + 1 < legacyText.length()) {
                index++;
                continue;
            }
            visible++;
        }
        return visible;
    }
}
