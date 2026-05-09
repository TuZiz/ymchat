package ym.ymchat.command;

import java.util.Arrays;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;

public final class ReplyCommand implements CommandExecutor {

    private final YmChatPlugin plugin;

    public ReplyCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandMessages.requirePermission(plugin, sender, "ymchat.use")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.reply.only-player");
            return true;
        }
        if (args.length == 0) {
            CommandMessages.sendKey(plugin, sender, "commands.reply.usage", "label", label);
            return true;
        }
        plugin.getPrivateMessageService().reply(player, String.join(" ", Arrays.copyOfRange(args, 0, args.length)));
        return true;
    }
}
