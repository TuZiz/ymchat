package ym.ymchat.service.showcase;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ShowcaseSource {

    UUID playerId();

    String playerName();

    boolean hasPermission(String permission);

    ItemStack mainHand();

    ItemStack[] storageContents();

    ItemStack[] armorContents();

    ItemStack offHand();

    ItemStack[] enderChestContents();

    String worldName();

    int blockX();

    int blockY();

    int blockZ();

    static ShowcaseSource of(Player player) {
        return new ShowcaseSource() {
            @Override
            public UUID playerId() {
                return player.getUniqueId();
            }

            @Override
            public String playerName() {
                return player.getName();
            }

            @Override
            public boolean hasPermission(String permission) {
                return player.hasPermission(permission);
            }

            @Override
            public ItemStack mainHand() {
                return player.getInventory().getItemInMainHand();
            }

            @Override
            public ItemStack[] storageContents() {
                return player.getInventory().getStorageContents();
            }

            @Override
            public ItemStack[] armorContents() {
                return player.getInventory().getArmorContents();
            }

            @Override
            public ItemStack offHand() {
                return player.getInventory().getItemInOffHand();
            }

            @Override
            public ItemStack[] enderChestContents() {
                return player.getEnderChest().getContents();
            }

            @Override
            public String worldName() {
                return player.getWorld().getName();
            }

            @Override
            public int blockX() {
                return player.getLocation().getBlockX();
            }

            @Override
            public int blockY() {
                return player.getLocation().getBlockY();
            }

            @Override
            public int blockZ() {
                return player.getLocation().getBlockZ();
            }
        };
    }
}
