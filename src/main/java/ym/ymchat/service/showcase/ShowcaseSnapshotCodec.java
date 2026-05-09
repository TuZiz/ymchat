package ym.ymchat.service.showcase;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class ShowcaseSnapshotCodec {

    public String encodeInventory(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack offHand) {
        YamlConfiguration configuration = new YamlConfiguration();
        writeArray(configuration, "storage", storageContents);
        writeArray(configuration, "armor", armorContents);
        writeItem(configuration, "offhand", offHand);
        return configuration.saveToString();
    }

    public InventoryPayload decodeInventory(String payload) {
        YamlConfiguration configuration = load(payload);
        return new InventoryPayload(
            readArray(configuration.getConfigurationSection("storage")),
            readArray(configuration.getConfigurationSection("armor")),
            readItem(configuration.getConfigurationSection("offhand"))
        );
    }

    public String encodeEnderChest(ItemStack[] contents) {
        YamlConfiguration configuration = new YamlConfiguration();
        writeArray(configuration, "contents", contents);
        return configuration.saveToString();
    }

    public EnderChestPayload decodeEnderChest(String payload) {
        YamlConfiguration configuration = load(payload);
        return new EnderChestPayload(readArray(configuration.getConfigurationSection("contents")));
    }

    private YamlConfiguration load(String payload) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(payload == null ? "" : payload);
        } catch (Exception ignored) {
            return new YamlConfiguration();
        }
        return configuration;
    }

    private void writeArray(YamlConfiguration configuration, String path, ItemStack[] contents) {
        ItemStack[] cloned = cloneArray(contents);
        configuration.set(path + ".size", cloned.length);
        for (int index = 0; index < cloned.length; index++) {
            writeItem(configuration, path + ".items." + index, cloned[index]);
        }
    }

    private ItemStack[] readArray(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack[0];
        }
        int size = section.getInt("size", 0);
        ItemStack[] items = new ItemStack[Math.max(0, size)];
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection == null) {
            return items;
        }
        for (int index = 0; index < items.length; index++) {
            items[index] = readItem(itemsSection.getConfigurationSection(String.valueOf(index)));
        }
        return items;
    }

    private void writeItem(YamlConfiguration configuration, String path, ItemStack item) {
        if (item == null) {
            return;
        }
        configuration.createSection(path, new LinkedHashMap<>(item.serialize()));
    }

    @SuppressWarnings("unchecked")
    private ItemStack readItem(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>(section.getValues(false));
        Object meta = values.get("meta");
        if (meta instanceof ConfigurationSection metaSection) {
            values.put("meta", new LinkedHashMap<>(metaSection.getValues(true)));
        }
        try {
            return cloneItem(ItemStack.deserialize(values));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null || source.length == 0) {
            return new ItemStack[0];
        }
        return Arrays.stream(source)
            .map(ShowcaseSnapshotCodec::cloneItem)
            .toArray(ItemStack[]::new);
    }

    static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    public record InventoryPayload(
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack offHand
    ) {

        public InventoryPayload {
            storageContents = cloneArray(storageContents);
            armorContents = cloneArray(armorContents);
            offHand = cloneItem(offHand);
        }
    }

    public record EnderChestPayload(ItemStack[] contents) {

        public EnderChestPayload {
            contents = cloneArray(contents);
        }
    }
}
