package ym.ymchat.service.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.ChatConfigLoader;
import ym.ymchat.config.filter.FilterCloudSettings;
import ym.ymchat.config.filter.FilterRule;
import ym.ymchat.config.filter.FilterSettings;

class TextFilterServiceTest {

    @Test
    void cloudWordsCanBlockConfiguredBadWords() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("badword")));
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of()
        );

        TextFilterService.FilterResult result = service.apply(player(false), "this has badword", "public", "global", settings);

        assertFalse(result.allowed());
        assertEquals("blocked", result.message());
    }

    @Test
    void cloudWordsCanReplaceConfiguredBadWords() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("badword")));
        FilterSettings settings = new FilterSettings(
            true,
            cloud("replace"),
            List.of()
        );

        TextFilterService.FilterResult result = service.apply(player(false), "this has badword", "public", "global", settings);

        assertTrue(result.allowed());
        assertEquals("this has ***", result.message());
    }

    @Test
    void linksAreAllowedWhenNoStaticLinkRuleExists() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("badword")));
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of()
        );

        TextFilterService.FilterResult result = service.apply(player(false), "qq vx discord.gg", "public", "global", settings);

        assertTrue(result.allowed());
        assertEquals("qq vx discord.gg", result.message());
    }

    @Test
    void cloudWordsCanBlockSeparatedBadWords() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("nmsl")));
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of()
        );

        TextFilterService.FilterResult result = service.apply(player(false), "n m-s_l", "public", "global", settings);

        assertFalse(result.allowed());
        assertEquals("blocked", result.message());
    }

    @Test
    void shortCloudWordsRemainLiteralOnly() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("sb")));
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of()
        );

        TextFilterService.FilterResult result = service.apply(player(false), "s b", "public", "global", settings);

        assertTrue(result.allowed());
        assertEquals("s b", result.message());
    }

    @Test
    void localRulesProvideFallbackWhenCloudIsEmpty() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of()));
        FilterRule rule = new FilterRule(
            "profanity-basic",
            "all",
            "block",
            "(?iu)n[\\s\\p{Punct}]*m[\\s\\p{Punct}]*s[\\s\\p{Punct}]*l",
            true,
            false,
            "***",
            "blocked",
            "ymchat.filter.bypass",
            List.of()
        );
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of(rule)
        );

        TextFilterService.FilterResult result = service.apply(player(false), "n-m-s-l", "public", "global", settings);

        assertFalse(result.allowed());
        assertEquals(List.of("profanity-basic"), result.hits());
    }

    @Test
    void bundledRulesContainWorkingLocalFallback() throws Exception {
        YamlConfiguration rules = new YamlConfiguration();
        rules.loadFromString(Files.readString(Path.of("src/main/resources/rules.yml"), StandardCharsets.UTF_8));
        FilterSettings settings = new ChatConfigLoader().load(rules).filterSettings();
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of()));

        TextFilterService.FilterResult result = service.apply(player(false), "n-m-s-l", "public", "global", settings);

        assertFalse(result.allowed());
        assertEquals(List.of("profanity-basic"), result.hits());
    }

    @Test
    void wildcardRuleChannelAppliesToEveryChannel() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of()));
        FilterRule rule = new FilterRule(
            "wildcard",
            "all",
            "block",
            "badword",
            false,
            false,
            "***",
            "blocked",
            "ymchat.filter.bypass",
            List.of("*")
        );
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of(rule)
        );

        TextFilterService.FilterResult result = service.apply(player(false), "badword", "public", "global", settings);

        assertFalse(result.allowed());
        assertEquals(List.of("wildcard"), result.hits());
    }

    @Test
    void bypassPermissionSkipsLocalRulesAndCloudWords() {
        TextFilterService service = new TextFilterService(new FilterCloudWordService(null, List.of("badword")));
        FilterRule rule = new FilterRule(
            "local",
            "all",
            "block",
            "badword",
            false,
            false,
            "***",
            "blocked",
            "ymchat.filter.bypass",
            List.of()
        );
        FilterSettings settings = new FilterSettings(
            true,
            cloud("block"),
            List.of(rule)
        );

        TextFilterService.FilterResult result = service.apply(player(true), "badword", "public", "global", settings);

        assertTrue(result.allowed());
        assertEquals("badword", result.message());
    }

    @Test
    void parsesCloudDatabaseWordsArray() {
        List<String> words = FilterCloudWordService.parseStringArray("""
            {"lastUpdateDate":"2023/7/31 20:00:00","words":["fuck","傻逼","bad\\nword"]}
            """, "words");

        assertEquals(List.of("fuck", "傻逼", "bad\nword"), words);
    }

    private FilterCloudSettings cloud(String mode) {
        return new FilterCloudSettings(
            true,
            "https://example.invalid/database.json",
            "words",
            60L,
            mode,
            "***",
            "blocked",
            "ymchat.filter.bypass",
            List.of("all")
        );
    }

    private Player player(boolean bypass) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> {
                if ("hasPermission".equals(method.getName())) {
                    return bypass;
                }
                if ("isOnline".equals(method.getName())) {
                    return true;
                }
                if ("toString".equals(method.getName())) {
                    return "TestPlayer";
                }
                return switch (method.getReturnType().getName()) {
                    case "boolean" -> false;
                    case "byte" -> (byte) 0;
                    case "short" -> (short) 0;
                    case "int" -> 0;
                    case "long" -> 0L;
                    case "float" -> 0F;
                    case "double" -> 0D;
                    case "char" -> '\0';
                    default -> null;
                };
            }
        );
    }
}
