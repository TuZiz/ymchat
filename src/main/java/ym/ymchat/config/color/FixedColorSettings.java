package ym.ymchat.config.color;

import java.util.List;

public record FixedColorSettings(
    boolean enabled,
    List<ColorPreset> rgbColors
) {

    public FixedColorSettings {
        rgbColors = rgbColors == null ? List.of() : List.copyOf(rgbColors);
    }

    public ColorPreset findRgbColor(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (ColorPreset color : rgbColors) {
            if (color.id() != null && color.id().equalsIgnoreCase(id)) {
                return color;
            }
        }
        return null;
    }

    public static FixedColorSettings defaults() {
        return new FixedColorSettings(
            true,
            List.of(
                new ColorPreset("pink", "&#FF99CCPink", "ymchat.color.rgb.pink", "#FF55FF"),
                new ColorPreset("sky", "&#33CCFFSky", "ymchat.color.rgb.sky", "#55CCFF")
            )
        );
    }

    public static FixedColorSettings nameDefaults() {
        return new FixedColorSettings(
            true,
            List.of(
                new ColorPreset("pink", "&#FF99CCPink", "ymchat.namecolor.rgb.pink", "#FF55FF"),
                new ColorPreset("sky", "&#33CCFFSky", "ymchat.namecolor.rgb.sky", "#55CCFF")
            )
        );
    }
}
