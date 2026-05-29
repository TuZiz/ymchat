package ym.ymchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import ym.ymchat.config.color.ColorChatSettings;
import ym.ymchat.config.color.ColorPreset;
import ym.ymchat.config.color.FixedColorSettings;
import ym.ymchat.service.color.ColorScope;
import ym.ymchat.service.color.PlayerColorPreference;
import ym.ymchat.service.color.PlayerColorPreferenceRepository;
import ym.ymchat.service.color.PlayerColorService;

class PlayerColorServiceTest {

    @Test
    void resolvesManualLegacyColorWhenPermissionExists() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.legacy("d"));

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker("ymchat.color.d"),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.MANUAL_LEGACY, resolved.source());
        assertEquals("&d", resolved.baseColorValue());
    }

    @Test
    void resolvesManualRgbColorWhenPermissionExists() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.rgb("pink"));

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker("ymchat.color.rgb.pink"),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.MANUAL_RGB, resolved.source());
        assertEquals("&#FF55FF", resolved.baseColorValue());
        assertEquals("pink", resolved.rgbColor().id());
    }

    @Test
    void manualOffFallsBackToRuleDefaultColor() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.off());

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker("ymchat.color.d"),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.MANUAL_OFF, resolved.source());
        assertEquals("&f", resolved.baseColorValue());
    }

    @Test
    void invalidStoredRgbFallsBackWithoutClearingPreference() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.rgb("missing"));

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker("ymchat.color.d"),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.RULE_DEFAULT, resolved.source());
        assertEquals("&f", resolved.baseColorValue());
        assertEquals("missing", repository.get(playerId).value());
    }

    @Test
    void mapsLegacyPresetStorageWithoutWritingDuringResolve() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.preset("gold"));

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker("ymchat.color.6"),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.MANUAL_LEGACY, resolved.source());
        assertEquals("&6", resolved.baseColorValue());
        assertEquals(PlayerColorPreference.Mode.PRESET, repository.get(playerId).mode());
        assertEquals("gold", repository.get(playerId).value());
    }

    @Test
    void unauthorizedStoredLegacyColorFallsBackWithoutClearingPreference() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        UUID playerId = UUID.randomUUID();
        repository.save(playerId, PlayerColorPreference.legacy("d"));

        PlayerColorService.ResolvedColor resolved = service.resolve(
            playerId,
            permissionChecker(),
            ColorChatSettings.defaults(),
            "&f"
        );

        assertEquals(PlayerColorService.ColorSource.RULE_DEFAULT, resolved.source());
        assertEquals("&f", resolved.baseColorValue());
        assertEquals("d", repository.get(playerId).value());
    }

    @Test
    void setRgbRejectsUnconfiguredLegacyId() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer allowed = new FakePlayer("ymchat.color.use", "ymchat.color.d");

        assertFalse(service.setRgb(allowed.asPlayer(), ColorChatSettings.defaults(), "d"));
        assertNull(repository.get(allowed.id()));
    }

    @Test
    void setRgbRequiresConfiguredPermissionNode() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer allowed = new FakePlayer("ymchat.color.use", "ymchat.color.rgb.pink");
        FakePlayer denied = new FakePlayer("ymchat.color.use");

        assertTrue(service.setRgb(allowed.asPlayer(), ColorChatSettings.defaults(), "pink"));
        assertEquals("pink", repository.get(allowed.id()).value());
        assertFalse(service.setRgb(denied.asPlayer(), ColorChatSettings.defaults(), "pink"));
    }

    @Test
    void configuredLegacyIdIsSavedAsRgbPreference() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer player = new FakePlayer("ymchat.color.use", "ymchat.color.d");
        FixedColorSettings settings = new FixedColorSettings(
            true,
            List.of(new ColorPreset("d", "pink", "ymchat.color.d", "&d"))
        );

        assertTrue(service.setRgb(player.asPlayer(), ColorScope.CHAT, settings, "d"));

        PlayerColorPreference stored = repository.get(player.id(), ColorScope.CHAT);
        assertEquals(PlayerColorPreference.Mode.RGB, stored.mode());
        assertEquals("d", stored.value());
    }

    @Test
    void nameScopeUsesNamePermissionsAndIndependentStorage() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer player = new FakePlayer("ymchat.namecolor.use", "ymchat.namecolor.d");
        FixedColorSettings settings = new FixedColorSettings(
            true,
            List.of(new ColorPreset("d", "pink", "ymchat.namecolor.d", "&d"))
        );

        assertTrue(service.setRgb(player.asPlayer(), ColorScope.NAME, settings, "d"));
        assertEquals("d", repository.get(player.id(), ColorScope.NAME).value());
        assertNull(repository.get(player.id(), ColorScope.CHAT));
    }

    @Test
    void opCanUseConfiguredColorWithoutExplicitPermissionNode() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer op = new FakePlayer(true, "ymchat.color.use");
        FixedColorSettings settings = new FixedColorSettings(
            true,
            List.of(new ColorPreset("e", "yellow", "ymchat.color.e", "&e"))
        );

        assertTrue(service.setRgb(op.asPlayer(), ColorScope.CHAT, settings, "e"));
        assertEquals("e", repository.get(op.id()).value());
    }

    @Test
    void opCanUseConfiguredRgbColorWithoutExplicitPermissionNode() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer op = new FakePlayer(true, "ymchat.color.use");

        assertTrue(service.setRgb(op.asPlayer(), ColorChatSettings.defaults(), "pink"));
        assertEquals("pink", repository.get(op.id()).value());
    }

    @Test
    void resetRemovesStoredPreferenceInsteadOfSavingOffMode() {
        InMemoryRepository repository = new InMemoryRepository();
        PlayerColorService service = new PlayerColorService(repository);
        FakePlayer player = new FakePlayer("ymchat.color.use", "ymchat.color.d");
        repository.save(player.id(), PlayerColorPreference.legacy("d"));

        service.reset(player.asPlayer());

        assertNull(repository.get(player.id()));
    }

    private Predicate<String> permissionChecker(String... permissions) {
        return permission -> java.util.Arrays.asList(permissions).contains(permission);
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

    private static final class FakePlayer {

        private final UUID id = UUID.randomUUID();
        private final boolean op;
        private final Set<String> permissions;

        private FakePlayer(String... permissions) {
            this(false, permissions);
        }

        private FakePlayer(boolean op, String... permissions) {
            this.op = op;
            this.permissions = Set.of(permissions);
        }

        private UUID id() {
            return id;
        }

        private Player asPlayer() {
            return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hasPermission" -> permissions.contains(String.valueOf(args[0]));
                    case "isOp" -> op;
                    case "isOnline" -> true;
                    default -> defaultValue(method.getReturnType());
                }
            );
        }
    }

    private static final class InMemoryRepository implements PlayerColorPreferenceRepository {

        private final Map<Key, PlayerColorPreference> values = new HashMap<>();

        @Override
        public PlayerColorPreference get(UUID playerId, ColorScope scope) {
            return values.get(new Key(playerId, scope));
        }

        @Override
        public void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) {
            values.put(new Key(playerId, scope), preference);
        }

        @Override
        public void remove(UUID playerId, ColorScope scope) {
            values.remove(new Key(playerId, scope));
        }

        private record Key(UUID playerId, ColorScope scope) {

            private Key {
                scope = scope == null ? ColorScope.CHAT : scope;
            }
        }
    }
}
