package ym.ymchat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ym.ymchat.YmChatPlugin;

@SuppressWarnings("deprecation")
public final class SpigotChatListener implements Listener {

    private final YmChatPlugin plugin;

    public SpigotChatListener(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        if (!plugin.isParticipating(sender)) {
            return;
        }

        event.setCancelled(true);
        plugin.handleCapturedChat(sender, event.getMessage());
    }
}
