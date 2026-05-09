package ym.ymchat.config.showcase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ShowcasePreviewLayout(
    ViewLayout inventory,
    ViewLayout enderChest,
    Map<String, String> typeDisplays
) {

    public ShowcasePreviewLayout {
        inventory = inventory == null ? defaults().inventory() : inventory;
        enderChest = enderChest == null ? defaults().enderChest() : enderChest;
        typeDisplays = typeDisplays == null || typeDisplays.isEmpty()
            ? Map.of("inventory", "Inventory", "ender-chest", "Ender Chest", "none", "None")
            : Collections.unmodifiableMap(new LinkedHashMap<>(typeDisplays));
    }

    public static ShowcasePreviewLayout defaults() {
        return new ShowcasePreviewLayout(
            new ViewLayout(
                "&#555555%player_name%'s inventory snapshot",
                54,
                Map.of(
                    "a", List.of(2, 3, 4, 5),
                    "o", List.of(6),
                    "s", List.of(9, 10, 11, 12, 13, 14, 15, 16, 17,
                        18, 19, 20, 21, 22, 23, 24, 25, 26,
                        27, 28, 29, 30, 31, 32, 33, 34, 35),
                    "h", List.of(36, 37, 38, 39, 40, 41, 42, 43, 44)
                ),
                List.of(
                    new StaticItemConfig(0, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(1, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(7, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(8, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(45, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(46, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(47, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(49, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(51, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(52, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(53, "BLACK_STAINED_GLASS_PANE", " ", List.of())
                ),
                new GuiButtonConfig(48, "BOOK", "&#33CCFFSnapshot Info", List.of(
                    "&#B0B0B0Player: &#FFFFFF%player_name%",
                    "&#B0B0B0Server: &#FFFFFF%server_name%",
                    "&#B0B0B0Type: &#FFFFFF%snapshot_type%",
                    "&#B0B0B0Created: &#FFFFFF%created_at%"
                )),
                new GuiButtonConfig(50, "BARRIER", "&#FF6B6BClose Preview", List.of("&#B0B0B0Close this showcase snapshot."))
            ),
            new ViewLayout(
                "&#555555%player_name%'s ender chest snapshot",
                45,
                Map.of("e", List.of(
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35
                )),
                List.of(
                    new StaticItemConfig(0, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(1, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(2, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(3, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(4, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(5, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(6, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(7, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(8, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(36, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(37, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(38, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(40, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(42, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(43, "BLACK_STAINED_GLASS_PANE", " ", List.of()),
                    new StaticItemConfig(44, "BLACK_STAINED_GLASS_PANE", " ", List.of())
                ),
                new GuiButtonConfig(39, "BOOK", "&#33CCFFSnapshot Info", List.of(
                    "&#B0B0B0Player: &#FFFFFF%player_name%",
                    "&#B0B0B0Server: &#FFFFFF%server_name%",
                    "&#B0B0B0Type: &#FFFFFF%snapshot_type%",
                    "&#B0B0B0Created: &#FFFFFF%created_at%"
                )),
                new GuiButtonConfig(41, "BARRIER", "&#FF6B6BClose Preview", List.of("&#B0B0B0Close this showcase snapshot."))
            ),
            Map.of("inventory", "Inventory", "ender-chest", "Ender Chest", "none", "None")
        );
    }

    public record ViewLayout(
        String title,
        int size,
        Map<String, List<Integer>> contentSlots,
        List<StaticItemConfig> staticItems,
        GuiButtonConfig summary,
        GuiButtonConfig close
    ) {

        public ViewLayout {
            contentSlots = contentSlots == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(contentSlots));
            staticItems = staticItems == null ? List.of() : List.copyOf(staticItems);
            summary = summary == null ? new GuiButtonConfig(-1, "BOOK", "&#33CCFFSnapshot Info", List.of(
                "&#B0B0B0Player: &#FFFFFF%player_name%",
                "&#B0B0B0Server: &#FFFFFF%server_name%",
                "&#B0B0B0Type: &#FFFFFF%snapshot_type%",
                "&#B0B0B0Created: &#FFFFFF%created_at%"
            )) : summary;
            close = close == null ? new GuiButtonConfig(-1, "BARRIER", "&#FF6B6BClose Preview", List.of("&#B0B0B0Close this showcase snapshot.")) : close;
        }
    }

    public record StaticItemConfig(
        int slot,
        String material,
        String name,
        List<String> lore
    ) {

        public StaticItemConfig {
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }

    public record GuiButtonConfig(
        int slot,
        String material,
        String name,
        List<String> lore
    ) {

        public GuiButtonConfig {
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }
}