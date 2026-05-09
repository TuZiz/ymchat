package ym.ymchat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ym.ymchat.YmChatPlugin;

public final class PlayerColorLifecycleListener implements Listener {

    private final YmChatPlugin plugin;

    public PlayerColorLifecycleListener(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        plugin.getPlayerColorPreferenceRepository().preloadBlocking(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerColorPreferenceRepository().clearRuntime(event.getPlayer().getUniqueId());
    }
}
