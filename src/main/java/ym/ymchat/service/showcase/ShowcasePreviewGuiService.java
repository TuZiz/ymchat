package ym.ymchat.service.showcase;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.config.showcase.ShowcasePreviewLayout;
import ym.ymchat.config.showcase.ShowcasePreviewLayout.GuiButtonConfig;
import ym.ymchat.config.showcase.ShowcasePreviewLayout.StaticItemConfig;
import ym.ymchat.config.showcase.ShowcasePreviewLayout.ViewLayout;
import ym.ymchat.config.showcase.ShowcasePreviewLayoutLoader;

import ym.ymchat.service.text.RichText;
public final class ShowcasePreviewGuiService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withLocale(Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault());

    private final YmChatPlugin plugin;
    private final File layoutFile;
    private final ShowcasePreviewLayoutLoader layoutLoader;
    private ShowcasePreviewLayout layout;

    public ShowcasePreviewGuiService(YmChatPlugin plugin) {
        this.plugin = plugin;
        this.layoutFile = new File(plugin.getDataFolder(), "gui/showcase-preview.yml");
        this.layoutLoader = new ShowcasePreviewLayoutLoader();
        this.layout = ShowcasePreviewLayout.defaults();
    }

    public void reloadLayout() {
        this.layout = layoutLoader.load(YamlConfiguration.loadConfiguration(layoutFile), plugin.getLanguageService());
    }

    public boolean isPreviewMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ShowcasePreviewHolder;
    }

    public void handleClick(Player player, Inventory inventory, int rawSlot) {
        if (!(inventory.getHolder() instanceof ShowcasePreviewHolder holder)) {
            return;
        }
        if (rawSlot == holder.closeSlot()) {
            player.closeInventory();
        }
    }

    public void openInventory(
        Player viewer,
        ShowcaseStoredSnapshot snapshot,
        ShowcaseSnapshotCodec.InventoryPayload payload
    ) {
        open(viewer, currentLayout().inventory(), snapshot, inventoryContent(payload));
    }

    public void openEnderChest(
        Player viewer,
        ShowcaseStoredSnapshot snapshot,
        ShowcaseSnapshotCodec.EnderChestPayload payload
    ) {
        open(viewer, currentLayout().enderChest(), snapshot, Map.of(
            "e", ShowcaseSnapshotCodec.cloneArray(payload.contents())
        ));
    }

    private void open(
        Player viewer,
        ViewLayout viewLayout,
        ShowcaseStoredSnapshot snapshot,
        Map<String, ItemStack[]> content
    ) {
        ShowcasePreviewHolder holder = new ShowcasePreviewHolder(viewLayout.close().slot());
        Inventory inventory = Bukkit.createInventory(
            holder,
            viewLayout.size(),
            RichText.toLegacySectionString(applyPlaceholders(viewLayout.title(), snapshot))
        );
        holder.setInventory(inventory);

        for (StaticItemConfig staticItem : viewLayout.staticItems()) {
            setItemIfValid(inventory, staticItem.slot(), buildItem(staticItem.material(), staticItem.name(), staticItem.lore(), false, snapshot));
        }
        setItemIfValid(inventory, viewLayout.summary().slot(), buildItem(
            viewLayout.summary().material(),
            viewLayout.summary().name(),
            viewLayout.summary().lore(),
            true,
            snapshot
        ));
        setItemIfValid(inventory, viewLayout.close().slot(), buildItem(
            viewLayout.close().material(),
            viewLayout.close().name(),
            viewLayout.close().lore(),
            false,
            snapshot
        ));

        for (Map.Entry<String, List<Integer>> entry : viewLayout.contentSlots().entrySet()) {
            ItemStack[] items = content.get(entry.getKey());
            if (items == null) {
                continue;
            }
            List<Integer> slots = entry.getValue();
            for (int index = 0; index < Math.min(slots.size(), items.length); index++) {
                ItemStack item = items[index];
                if (item != null && item.getType() != Material.AIR) {
                    inventory.setItem(slots.get(index), item.clone());
                }
            }
        }

        viewer.openInventory(inventory);
    }

    private Map<String, ItemStack[]> inventoryContent(ShowcaseSnapshotCodec.InventoryPayload payload) {
        Map<String, ItemStack[]> content = new HashMap<>();
        content.put("s", mapStorageRows(payload.storageContents()));
        content.put("h", mapHotbar(payload.storageContents()));
        content.put("a", mapArmor(payload.armorContents()));
        content.put("o", new ItemStack[]{ShowcaseSnapshotCodec.cloneItem(payload.offHand())});
        return content;
    }

    private ItemStack[] mapStorageRows(ItemStack[] storageContents) {
        ItemStack[] rows = new ItemStack[27];
        ItemStack[] source = ShowcaseSnapshotCodec.cloneArray(storageContents);
        for (int index = 0; index < rows.length; index++) {
            int sourceIndex = index + 9;
            rows[index] = sourceIndex < source.length ? source[sourceIndex] : null;
        }
        return rows;
    }

    private ItemStack[] mapHotbar(ItemStack[] storageContents) {
        ItemStack[] hotbar = new ItemStack[9];
        ItemStack[] source = ShowcaseSnapshotCodec.cloneArray(storageContents);
        for (int index = 0; index < hotbar.length; index++) {
            hotbar[index] = index < source.length ? source[index] : null;
        }
        return hotbar;
    }

    private ItemStack[] mapArmor(ItemStack[] armorContents) {
        ItemStack[] mapped = new ItemStack[4];
        ItemStack[] source = ShowcaseSnapshotCodec.cloneArray(armorContents);
        // Bukkit armor order is boots, leggings, chestplate, helmet.
        mapped[0] = source.length > 3 ? source[3] : null;
        mapped[1] = source.length > 2 ? source[2] : null;
        mapped[2] = source.length > 1 ? source[1] : null;
        mapped[3] = source.length > 0 ? source[0] : null;
        return mapped;
    }

    private ShowcasePreviewLayout currentLayout() {
        return layout == null ? ShowcasePreviewLayout.defaults() : layout;
    }

    private void setItemIfValid(Inventory inventory, int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize() || item == null) {
            return;
        }
        inventory.setItem(slot, item);
    }

    private ItemStack buildItem(String materialName, String name, List<String> lore, boolean highlighted, ShowcaseStoredSnapshot snapshot) {
        Material material = resolveMaterial(materialName, Material.STONE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(RichText.toLegacySectionString(applyPlaceholders(name, snapshot)));
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(line -> RichText.toLegacySectionString(applyPlaceholders(line, snapshot))).toList());
        }
        if (highlighted) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Material resolveMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String applyPlaceholders(String value, ShowcaseStoredSnapshot snapshot) {
        if (value == null) {
            return "";
        }
        return value
            .replace("%player_name%", snapshot.senderName() == null ? "" : snapshot.senderName())
            .replace("%server_name%", snapshot.serverName() == null ? "" : snapshot.serverName())
            .replace("%server_id%", snapshot.serverId() == null ? "" : snapshot.serverId())
            .replace("%snapshot_id%", snapshot.snapshotId() == null ? "" : snapshot.snapshotId())
            .replace("%snapshot_type%", snapshotTypeDisplay(snapshot.snapshotType()))
            .replace("%created_at%", TIME_FORMATTER.format(snapshot.createdAt()));
    }

    private String snapshotTypeDisplay(String snapshotType) {
        Map<String, String> typeDisplays = currentLayout().typeDisplays();
        if ("inventory".equalsIgnoreCase(snapshotType)) {
            return typeDisplays.getOrDefault("inventory", "Inventory");
        }
        if ("ender-chest".equalsIgnoreCase(snapshotType)) {
            return typeDisplays.getOrDefault("ender-chest", "Ender Chest");
        }
        return typeDisplays.getOrDefault("none", plugin.getLanguageService().get("common.none"));
    }

    public static final class ShowcasePreviewHolder implements InventoryHolder {

        private final int closeSlot;
        private Inventory inventory;

        public ShowcasePreviewHolder(int closeSlot) {
            this.closeSlot = closeSlot;
        }

        public int closeSlot() {
            return closeSlot;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
