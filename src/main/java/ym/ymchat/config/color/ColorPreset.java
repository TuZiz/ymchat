package ym.ymchat.config.color;

import java.util.List;

public record ColorPreset(
    String id,
    String display,
    String permission,
    String value,
    List<String> gradientColors
) {

    public ColorPreset {
        gradientColors = gradientColors == null ? List.of() : List.copyOf(gradientColors);
    }

    public ColorPreset(String id, String display, String permission, String value) {
        this(id, display, permission, value, List.of());
    }

    public boolean hasGradient() {
        return gradientColors.size() >= 2;
    }
}
