package ym.ymchat.config.chat;

public record MentionSettings(
    boolean enabled,
    String prefix,
    String highlightColor,
    String sound,
    boolean notifyActionbar,
    boolean allowEveryone,
    String everyonePermission,
    String everyoneToken
) {

    public static MentionSettings defaults() {
        return new MentionSettings(
            true,
            "@",
            "&e",
            "ENTITY_EXPERIENCE_ORB_PICKUP",
            true,
            true,
            "ymchat.mention.everyone",
            "all"
        );
    }
}
