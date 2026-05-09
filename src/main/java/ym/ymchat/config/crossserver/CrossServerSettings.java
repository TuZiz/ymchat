package ym.ymchat.config.crossserver;

public record CrossServerSettings(
    boolean enabled,
    String serverId,
    String serverName,
    int pollIntervalTicks,
    int batchSize,
    int retentionHours,
    boolean showOrigin,
    String originFormat,
    DatabaseSettings database,
    CrossServerLogSettings logs
) {

    public static CrossServerSettings defaults() {
        return new CrossServerSettings(
            false,
            "survival-1",
            "survival-1",
            20,
            100,
            24,
            true,
            "&#777777[&#33CCFF%origin_server%&#777777] ",
            DatabaseSettings.defaults(),
            CrossServerLogSettings.defaults()
        );
    }

    public boolean isConfigured() {
        return enabled
            && serverId != null
            && !serverId.isBlank()
            && database != null
            && database.isConfigured();
    }
}
