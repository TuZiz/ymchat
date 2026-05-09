package ym.ymchat.command;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;

public final class MessageCommand implements CommandExecutor, TabCompleter {

    private final YmChatPlugin plugin;

    public MessageCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandMessages.requirePermission(plugin, sender, "ymchat.use")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            CommandMessages.sendKey(plugin, sender, "commands.message.only-player");
            return true;
        }
        if (args.length < 2) {
            CommandMessages.sendKey(plugin, sender, "commands.message.usage", "label", label);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            CommandMessages.send(sender, plugin.getPrivateMessageService().offlineMessage(args[0]));
            return true;
        }
        plugin.getPrivateMessageService().sendPrivateMessage(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .sorted(String::compareToIgnoreCase)
                .toList();
        }
        return List.of();
    }
}
