package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import ym.ymchat.service.showcase.ShowcaseSnapshotCodec;

class ShowcaseSnapshotCodecTest {

    @Test
    void inventoryPayloadClonesIncomingArrays() {
        ItemStack[] storage = new ItemStack[]{new ItemStack(Material.DIAMOND, 3)};
        ItemStack[] armor = new ItemStack[]{new ItemStack(Material.IRON_BOOTS), null, null, null};
        ItemStack offHand = new ItemStack(Material.SHIELD);

        ShowcaseSnapshotCodec.InventoryPayload payload = new ShowcaseSnapshotCodec.InventoryPayload(storage, armor, offHand);
        storage[0].setAmount(1);

        assertEquals(3, payload.storageContents()[0].getAmount());
        assertEquals(Material.IRON_BOOTS, payload.armorContents()[0].getType());
        assertEquals(Material.SHIELD, payload.offHand().getType());
        assertNotSame(storage[0], payload.storageContents()[0]);
    }

    @Test
    void enderChestPayloadClonesIncomingArray() {
        ItemStack[] contents = new ItemStack[]{new ItemStack(Material.ENDER_PEARL, 16)};

        ShowcaseSnapshotCodec.EnderChestPayload payload = new ShowcaseSnapshotCodec.EnderChestPayload(contents);
        contents[0].setAmount(1);

        assertEquals(Material.ENDER_PEARL, payload.contents()[0].getType());
        assertEquals(16, payload.contents()[0].getAmount());
        assertNotSame(contents[0], payload.contents()[0]);
    }
}
