package ym.ymchat.command;

import org.bukkit.command.CommandSender;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.service.text.RichText;

public final class CommandMessages {

    private CommandMessages() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(RichText.toLegacySectionString(message));
    }

    public static void sendKey(YmChatPlugin plugin, CommandSender sender, String key, String... placeholders) {
        send(sender, plugin.getLanguageService().get(key, placeholders));
    }

    public static boolean requirePermission(YmChatPlugin plugin, CommandSender sender, String permission) {
        if (permission == null || permission.isBlank() || sender.hasPermission(permission)) {
            return true;
        }
        sendKey(plugin, sender, "commands.common.no-permission");
        return false;
    }
}
