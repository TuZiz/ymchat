package ym.ymchat.config.chat;

public record ChannelSwitchSettings(
    boolean enabled,
    String adminPermission,
    boolean crossServerAdminOnly
) {

    private static final String DEFAULT_ADMIN_PERMISSION = "ymchat.channel.admin";

    public ChannelSwitchSettings {
        adminPermission = adminPermission == null || adminPermission.isBlank()
            ? DEFAULT_ADMIN_PERMISSION
            : adminPermission;
    }

    public boolean isAdmin(boolean hasPermission) {
        return hasPermission;
    }

    public boolean canSwitch(boolean admin, ChatChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel.crossServer() && crossServerAdminOnly && !admin) {
            return false;
        }
        return enabled || admin;
    }

    public static ChannelSwitchSettings defaults() {
        return new ChannelSwitchSettings(false, DEFAULT_ADMIN_PERMISSION, true);
    }
}
