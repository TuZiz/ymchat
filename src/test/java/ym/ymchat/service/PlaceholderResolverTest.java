package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import ym.ymchat.service.text.PlaceholderResolver;

class PlaceholderResolverTest {

    @Test
    void resolvesOfflinePlayerAndBracketPlaceholderApiMethods() {
        Player player = fakePlayer("Tester");

        String resolved = PlaceholderResolver.resolve(
            player,
            "name=%player_name%, value=%db_value%, bracket={db_value}, number=%db_number%"
        );

        assertEquals("name=Tester, value=db-Tester, bracket=bracket-Tester, number=42", resolved);
    }

    @Test
    void ignoresBrokenPlaceholderApiExpansionAndKeepsResolvedBuiltins() {
        Player player = fakePlayer("Tester");

        String resolved = PlaceholderResolver.resolve(player, "name=%player_name%, bad=%explode%");

        assertEquals("name=Tester, bad=%explode%", resolved);
    }

    private Player fakePlayer(String name) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getDisplayName" -> name;
                case "getPing" -> 12;
                case "getHealth" -> 20D;
                case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000123");
                case "isOnline" -> true;
                case "getPlayer" -> proxy;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "FakePlayer(" + name + ")";
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
