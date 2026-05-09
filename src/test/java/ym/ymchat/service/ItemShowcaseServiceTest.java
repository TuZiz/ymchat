package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.showcase.ItemShowcaseSettings;
import ym.ymchat.service.showcase.ItemShowcaseService;
import ym.ymchat.service.showcase.PreparedShowcase;
import ym.ymchat.service.showcase.ShowcaseSource;
import ym.ymchat.service.showcase.ShowcaseType;
import ym.ymchat.service.showcase.SnapshotGateway;
import ym.ymchat.service.text.RichText;

class ItemShowcaseServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void replacesOnlyConfiguredMaximumTokens() {
        ItemShowcaseService service = new ItemShowcaseService();
        PreparedShowcase prepared = service.prepare(
            source(
                permission -> true,
                new ItemStack(Material.DIAMOND, 3),
                new ItemStack[36],
                new ItemStack[4],
                null,
                new ItemStack[27]
            ),
            "look [i] and :i:",
            settings(0L),
            1000L
        );

        Component replaced = service.apply(Component.text("look [i] and :i:"), prepared);
        String serialized = RichText.serializeToSection(replaced);

        assertTrue(prepared.ready());
        assertTrue(serialized.contains("ITEM"));
        assertTrue(serialized.contains(":i:"));
    }

    @Test
    void inventoryTokenCreatesClickablePreviewCommand() {
        ItemShowcaseService service = new ItemShowcaseService(new FixedSnapshotGateway("inv-snapshot", "ec-snapshot"));
        PreparedShowcase prepared = service.prepare(
            source(
                permission -> true,
                new ItemStack(Material.DIAMOND, 1),
                new ItemStack[]{new ItemStack(Material.STONE, 64)},
                new ItemStack[4],
                null,
                new ItemStack[27]
            ),
            "show [inv]",
            settings(0L),
            1000L
        );

        String json = GsonComponentSerializer.gson().serialize(service.apply(Component.text("show [inv]"), prepared));

        assertTrue(prepared.ready());
        assertTrue(json.contains("/ymchat showcase open inventory inv-snapshot"));
    }

    @Test
    void positionTokenCreatesCopyEvent() {
        ItemShowcaseService service = new ItemShowcaseService();
        PreparedShowcase prepared = service.prepare(
            source(
                permission -> true,
                new ItemStack(Material.DIAMOND, 1),
                new ItemStack[36],
                new ItemStack[4],
                null,
                new ItemStack[27]
            ),
            "pos [pos]",
            settings(0L),
            1000L
        );

        String json = GsonComponentSerializer.gson().serialize(service.apply(Component.text("pos [pos]"), prepared));

        assertTrue(prepared.ready());
        assertTrue(json.contains("\"copy_to_clipboard\""));
        assertTrue(json.contains("world 12 64 -9"));
    }

    @Test
    void cooldownsAreTrackedPerShowcaseType() {
        ItemShowcaseService service = new ItemShowcaseService();
        ItemShowcaseSettings settings = settings(3000L);
        service.markUsed(PLAYER_ID, ShowcaseType.ITEM, 1000L);

        PreparedShowcase itemPrepared = service.prepare(
            source(permission -> !permission.endsWith(".bypass"), new ItemStack(Material.DIAMOND), new ItemStack[36], new ItemStack[4], null, new ItemStack[27]),
            "[i]",
            settings,
            1500L
        );
        PreparedShowcase inventoryPrepared = service.prepare(
            source(permission -> !permission.endsWith(".bypass"), new ItemStack(Material.DIAMOND), new ItemStack[]{new ItemStack(Material.STONE)}, new ItemStack[4], null, new ItemStack[27]),
            "[inv]",
            settings,
            1500L
        );

        assertTrue(itemPrepared.blocked());
        assertTrue(inventoryPrepared.ready());
    }

    @Test
    void blocksInventoryWhenPermissionIsMissing() {
        ItemShowcaseService service = new ItemShowcaseService(new FixedSnapshotGateway("inv", "ec"));
        PreparedShowcase prepared = service.prepare(
            source(
                permission -> !"ymchat.showcase.inventory".equals(permission),
                new ItemStack(Material.DIAMOND),
                new ItemStack[]{new ItemStack(Material.STONE)},
                new ItemStack[4],
                null,
                new ItemStack[27]
            ),
            "[inv]",
            settings(0L),
            1000L
        );

        assertTrue(prepared.blocked());
        assertTrue(prepared.blockedMessage().contains("inventory permission"));
    }

    @Test
    void blocksEnderChestWhenEmpty() {
        ItemShowcaseService service = new ItemShowcaseService(new FixedSnapshotGateway("inv", "ec"));
        PreparedShowcase prepared = service.prepare(
            source(permission -> true, new ItemStack(Material.DIAMOND), new ItemStack[36], new ItemStack[4], null, new ItemStack[27]),
            "[ec]",
            settings(0L),
            1000L
        );

        assertTrue(prepared.blocked());
        assertTrue(prepared.blockedMessage().contains("ender chest content"));
    }

    @Test
    void ignoresMessagesWithoutTokens() {
        ItemShowcaseService service = new ItemShowcaseService();
        PreparedShowcase prepared = service.prepare(
            source(permission -> true, new ItemStack(Material.DIAMOND), new ItemStack[36], new ItemStack[4], null, new ItemStack[27]),
            "hello",
            settings(0L),
            1000L
        );

        assertFalse(prepared.requested());
    }

    private ItemShowcaseSettings settings(long itemCooldownMillis) {
        return new ItemShowcaseSettings(
            true,
            "Use [i] to showcase item",
            "Showcase disabled",
            "Only players can showcase content",
            new ItemShowcaseSettings.ItemSection(
                true,
                "ymchat.item.showcase",
                "ymchat.item.showcase.bypass",
                itemCooldownMillis,
                List.of("[i]", ":i:"),
                1,
                "&eITEM",
                "No item permission",
                "Empty hand",
                "Item cooldown %remaining_seconds%"
            ),
            new ItemShowcaseSettings.SnapshotSection(
                true,
                "ymchat.showcase.inventory",
                "ymchat.showcase.inventory.bypass",
                5000L,
                List.of("[inv]"),
                1,
                "&b[Inventory]",
                List.of("&7Player: &f%player_name%"),
                "No inventory permission",
                "No inventory content",
                "Inventory cooldown %remaining_seconds%"
            ),
            new ItemShowcaseSettings.SnapshotSection(
                true,
                "ymchat.showcase.enderchest",
                "ymchat.showcase.enderchest.bypass",
                5000L,
                List.of("[ec]"),
                1,
                "&d[Ender Chest]",
                List.of("&7Player: &f%player_name%"),
                "No ender chest permission",
                "No ender chest content",
                "Ender chest cooldown %remaining_seconds%"
            ),
            new ItemShowcaseSettings.PositionSection(
                true,
                "ymchat.showcase.position",
                "ymchat.showcase.position.bypass",
                1000L,
                List.of("[pos]"),
                1,
                "&a[%world% %x% %y% %z%]",
                List.of("&7Position: &f%x%, %y%, %z%"),
                "%world% %x% %y% %z%",
                "No position permission",
                "Position cooldown %remaining_seconds%"
            )
        );
    }

    private ShowcaseSource source(
        Predicate<String> permissionChecker,
        ItemStack mainHand,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack offHand,
        ItemStack[] enderChestContents
    ) {
        return new TestShowcaseSource(permissionChecker, mainHand, storageContents, armorContents, offHand, enderChestContents);
    }

    private record TestShowcaseSource(
        Predicate<String> permissionChecker,
        ItemStack mainHand,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack offHand,
        ItemStack[] enderChestContents
    ) implements ShowcaseSource {

        @Override
        public UUID playerId() {
            return PLAYER_ID;
        }

        @Override
        public String playerName() {
            return "Tu_zi";
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissionChecker.test(permission);
        }

        @Override
        public String worldName() {
            return "world";
        }

        @Override
        public int blockX() {
            return 12;
        }

        @Override
        public int blockY() {
            return 64;
        }

        @Override
        public int blockZ() {
            return -9;
        }
    }

    private record FixedSnapshotGateway(String inventoryId, String enderChestId) implements SnapshotGateway {

        @Override
        public String createInventorySnapshot(ShowcaseSource source, long now) {
            return inventoryId;
        }

        @Override
        public String createEnderChestSnapshot(ShowcaseSource source, long now) {
            return enderChestId;
        }
    }
}
