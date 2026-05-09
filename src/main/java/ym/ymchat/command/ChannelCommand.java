package ym.ymchat.command;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;

public final class ChannelCommand implements CommandExecutor, TabCompleter {

    private final YmChatPlugin plugin;

    public ChannelCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandMessages.requirePermission(plugin, sender, "ymchat.use")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.channel.only-player");
            return true;
        }
        if (args.length == 0) {
            CommandMessages.sendKey(
                plugin,
                sender,
                "commands.channel.current",
                "channel", plugin.getChannelService().describeChannel(plugin.getChannelService().resolveChannel(player))
            );
            CommandMessages.sendKey(
                plugin,
                sender,
                "commands.channel.available",
                "channels", String.join("&#AAAAAA, &#FFFFFF", plugin.getChannelService().availableChannelIds(player))
            );
            return true;
        }
        CommandMessages.send(sender, plugin.getChannelService().switchChannel(player, args[0]));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return plugin.getChannelService().availableChannelIds(player).stream()
                .filter(id -> id.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }
}
