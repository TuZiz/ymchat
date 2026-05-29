package ym.ymchat.service.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.ChatConfigLoader;
import ym.ymchat.config.ChatPluginConfig;
import ym.ymchat.config.chat.ChatChannel;
import ym.ymchat.config.chat.SectionStyle;
import ym.ymchat.service.color.PlayerColorPreference;
import ym.ymchat.service.color.PlayerColorService;

class ChatRendererTest {

    @Test
    void skipsCrossServerPrefixWhenItContainsLocalServerOrWorldPlaceholders() {
        SectionStyle section = new SectionStyle("~", "&f[&3%multiverse-core_alias%&f]%tags_current% ", null, null, null, null, null);

        assertFalse(ChatRenderer.shouldIncludeCrossServerPrefix(section));
    }

    @Test
    void keepsCrossServerPrefixWhenItOnlyContainsSharedPlayerDecorations() {
        SectionStyle section = new SectionStyle("~", "%tags_current% ", null, null, null, null, null);

        assertTrue(ChatRenderer.shouldIncludeCrossServerPrefix(section));
    }

    @Test
    void rendersNameColorTokensWithoutColoringLuckPermsPrefix() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Channels.List", List.of(Map.of("id", "global", "format", "default")));
        yaml.set("Channels.Formats", List.of(Map.of(
            "id", "default",
            "channel", "global",
            "priority", 10,
            "name", List.of(Map.of("text", "%luckperms_prefix%{name_color}%player_name%{name_reset}")),
            "message", Map.of("variants", List.of(Map.of("text", "&#FFFFFF: {message}")))
        )));
        ChatPluginConfig config = new ChatConfigLoader().load(yaml);
        ChatRenderer renderer = new ChatRenderer(
            (player, input) -> input
                .replace("%luckperms_prefix%", "&6[VIP] ")
                .replace("%player_name%", "Alice"),
            (player, ignored) -> new PlayerColorService.ResolvedColor(
                "&#FF00FF",
                PlayerColorService.ColorSource.MANUAL_RGB,
                PlayerColorPreference.rgb("pink"),
                null
            )
        );

        ChatChannel channel = config.defaultChannel();
        ChatRenderer.RenderedChat rendered = renderer.render(fakePlayer(), channel, Component.text("hello"), config);
        String json = GsonComponentSerializer.gson().serialize(rendered.component()).toLowerCase(Locale.ROOT);

        int prefixIndex = json.indexOf("\"text\":\"[vip] \"");
        int nameIndex = json.indexOf("\"text\":\"alice\"");
        assertTrue(prefixIndex >= 0);
        assertTrue(nameIndex > prefixIndex);
        assertTrue(json.contains("\"color\":\"gold\""));
        assertTrue(json.contains("\"color\":\"#ff00ff\""));
        assertFalse(json.contains("\"color\":\"#ff00ff\",\"text\":\"[vip] \""));
        assertFalse(json.contains("\"text\":\"[vip] \",\"color\":\"#ff00ff\""));
    }

    private Player fakePlayer() {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission", "isOnline" -> true;
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
