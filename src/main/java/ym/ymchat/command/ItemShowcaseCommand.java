package ym.ymchat.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.showcase.ItemShowcaseSettings;

public final class ItemShowcaseCommand implements CommandExecutor {

    private final YmChatPlugin plugin;

    public ItemShowcaseCommand(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ItemShowcaseSettings settings = plugin.getChatConfig() == null
            ? ItemShowcaseSettings.defaults()
            : plugin.getChatConfig().itemShowcaseSettings();
        if (!(sender instanceof Player player)) {
            CommandMessages.send(sender, settings.onlyPlayerMessage());
            return true;
        }
        if (!settings.enabled()) {
            CommandMessages.send(player, settings.disabledMessage());
            return true;
        }

        plugin.handleCapturedChat(player, settings.commandMessage());
        return true;
    }
}
