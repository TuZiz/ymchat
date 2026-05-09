package ym.ymchat.config.color;

public record ColorChatSettings(
    InlineColorSettings inlineSettings,
    FixedColorSettings fixedSettings
) {

    public static ColorChatSettings defaults() {
        return new ColorChatSettings(InlineColorSettings.defaults(), FixedColorSettings.defaults());
    }
}
