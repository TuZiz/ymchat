package ym.ymchat.config.color;

public record InlineColorSettings(
    String legacyPermission,
    String formatPermission,
    String rgbPermission
) {

    public static InlineColorSettings defaults() {
        return new InlineColorSettings(
            "ymchat.color.inline.legacy",
            "ymchat.color.inline.format",
            "ymchat.color.inline.rgb"
        );
    }
}
