package ym.ymchat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;

public final class SocialSpyCommand implements CommandExecutor {

    private final YmChatPlugin plugin;

    public SocialSpyCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandMessages.requirePermission(plugin, sender, "ymchat.socialspy")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.socialspy.only-player");
            return true;
        }
        boolean enabled = plugin.getPrivateMessageService().toggleSocialSpy(player);
        CommandMessages.sendKey(
            plugin,
            sender,
            "commands.socialspy.status",
            "value", plugin.getLanguageService().get(enabled ? "common.state.enabled" : "common.state.disabled")
        );
        return true;
    }
}
