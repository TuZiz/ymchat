package ym.ymchat.config.chat;

public record AntiSpamSettings(
    boolean enabled,
    String bypassPermission,
    long cooldownMillis,
    int maxLength,
    double capsRatio,
    int capsMinLetters,
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
            0D,
            8,
            30000L,
            false,
            "lang:anti-spam.messages.too-fast",
            "lang:anti-spam.messages.too-long",
            "lang:anti-spam.messages.too-many-caps",
            "lang:anti-spam.messages.duplicate"
        );
    }
}
