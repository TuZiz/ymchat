package ym.ymchat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ym.ymchat.YmChatPlugin;

public final class ShowcasePreviewListener implements Listener {

    private final YmChatPlugin plugin;

    public ShowcasePreviewListener(YmChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!plugin.getShowcasePreviewGuiService().isPreviewMenu(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        plugin.getShowcasePreviewGuiService().handleClick(player, event.getView().getTopInventory(), event.getRawSlot());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.getShowcasePreviewGuiService().isPreviewMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}
