package ym.ymchat.config.chat;

public record AntiSpamSettings(
    boolean enabled,
    String bypassPermission,
    long cooldownMillis,
    int maxLength,
    double capsRatio,
    long duplicateWindowMillis,
    boolean blockDuplicate,
    String tooFastMessage,
    String tooLongMessage,
    String tooManyCapsMessage,
    String duplicateMessage
) {

    public static AntiSpamSettings defaults() {
        return new AntiSpamSettings(
            true,
            "ymchat.bypass.antispam",
            1500L,
            120,
            0.7D,
            30000L,
            true,
            "&#777777[&#FFD700!&#777777] &#FFD700You are sending messages too quickly.",
            "&#777777[&#FFD700!&#777777] &#FFD700Your message is too long.",
            "&#777777[&#FFD700!&#777777] &#FFD700Please avoid excessive capital letters.",
            "&#777777[&#FFD700!&#777777] &#FFD700Please do not repeat the same message."
        );
    }
}
