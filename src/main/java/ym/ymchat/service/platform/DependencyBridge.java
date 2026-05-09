package ym.ymchat.service.platform;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import ym.ymchat.YmChatPlugin;

import ym.ymchat.service.text.PlaceholderResolver;
public final class DependencyBridge {

    private final YmChatPlugin plugin;

    public DependencyBridge(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        plugin.getLogger().info("YmChat hooks: PlaceholderAPI=" + pluginManager.isPluginEnabled("PlaceholderAPI")
            + ", LuckPerms=" + pluginManager.isPluginEnabled("LuckPerms"));
    }

    public String resolvePlaceholders(Player player, String input) {
        return PlaceholderResolver.resolve(player, input);
    }
}
