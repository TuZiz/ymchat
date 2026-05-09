package ym.ymchat.service.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerColorPreferenceCacheRepositoryTest {

    @Test
    void switchesBetweenCrossServerAndLocalBackends() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local", Map.of(playerId, PlayerColorPreference.legacy("d")));
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, PlayerColorPreference.legacy("e")));
        QueueingExecutor executor = new QueueingExecutor();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, id -> null);

        repository.rebind(true);
        repository.preloadBlocking(playerId);
        assertEquals("e", repository.get(playerId).value());

        repository.rebind(false);
        repository.preloadBlocking(playerId);
        assertEquals("d", repository.get(playerId).value());
    }

    @Test
    void usesLocalBackendSynchronouslyWhenCrossServerIsDisabled() {
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server");
        QueueingExecutor executor = new QueueingExecutor();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, id -> null);
        UUID playerId = UUID.randomUUID();

        repository.rebind(false);
        repository.save(playerId, PlayerColorPreference.legacy("d"));

        assertEquals("d", repository.get(playerId).value());
        assertEquals("d", localBackend.values().get(playerId).value());
        assertEquals(0, executor.size());
        assertNull(crossServerBackend.values().get(playerId));
    }

    @Test
    void usesCrossServerBackendWhenEnabled() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, PlayerColorPreference.rgb("pink")));
        QueueingExecutor executor = new QueueingExecutor();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, id -> null);

        repository.rebind(true);
        repository.preloadBlocking(playerId);
        repository.save(playerId, PlayerColorPreference.legacy("d"));

        assertEquals("d", repository.get(playerId).value());
        assertEquals("pink", crossServerBackend.values().get(playerId).value());

        executor.runAll();

        assertEquals("d", crossServerBackend.values().get(playerId).value());
        assertNull(localBackend.values().get(playerId));
    }

    @Test
    void removeDeletesCrossServerPreference() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, PlayerColorPreference.legacy("d")));
        QueueingExecutor executor = new QueueingExecutor();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, id -> null);

        repository.rebind(true);
        repository.preloadBlocking(playerId);
        repository.remove(playerId);

        assertNull(repository.get(playerId));

        executor.runAll();

        assertNull(crossServerBackend.values().get(playerId));
    }

    @Test
    void staleAsyncWriteDoesNotOverwriteLatestChoice() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server");
        QueueingExecutor executor = new QueueingExecutor();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, id -> null);

        repository.rebind(true);
        repository.save(playerId, PlayerColorPreference.legacy("d"));
        repository.save(playerId, PlayerColorPreference.legacy("e"));

        executor.runLast();
        executor.runFirst();

        assertEquals("e", repository.get(playerId).value());
        assertEquals("e", crossServerBackend.values().get(playerId).value());
    }

    @Test
    void preloadSkipsDirtyStateUntilAsyncWriteFinishes() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, PlayerColorPreference.legacy("a")));
        QueueingExecutor executor = new QueueingExecutor();
        Map<UUID, Player> onlinePlayers = new HashMap<>();
        PlayerColorPreferenceCacheRepository repository = repository(localBackend, crossServerBackend, executor, onlinePlayers::get);

        repository.rebind(true);
        repository.save(playerId, PlayerColorPreference.legacy("d"));
        repository.clearRuntime(playerId);
        repository.preloadBlocking(playerId);

        assertNull(repository.get(playerId));

        onlinePlayers.put(playerId, fakePlayer(playerId));
        executor.runAll();

        assertEquals("d", repository.get(playerId).value());
        assertEquals("d", crossServerBackend.values().get(playerId).value());
    }

    private PlayerColorPreferenceCacheRepository repository(
        InMemoryBackend localBackend,
        InMemoryBackend crossServerBackend,
        Executor executor,
        Function<UUID, Player> playerLookup
    ) {
        return new PlayerColorPreferenceCacheRepository(
            localBackend,
            crossServerBackend,
            executor,
            Logger.getLogger("PlayerColorPreferenceCacheRepositoryTest"),
            playerLookup
        );
    }

    private Player fakePlayer(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "isOnline" -> true;
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

    private static final class QueueingExecutor implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }

        private void runFirst() {
            tasks.removeFirst().run();
        }

        private void runLast() {
            tasks.removeLast().run();
        }

        private int size() {
            return tasks.size();
        }
    }

    private static final class InMemoryBackend implements PlayerColorPreferencePersistenceBackend {

        private final String description;
        private final Map<UUID, PlayerColorPreference> values = new HashMap<>();

        private InMemoryBackend(String description) {
            this(description, Map.of());
        }

        private InMemoryBackend(String description, Map<UUID, PlayerColorPreference> initialValues) {
            this.description = description;
            this.values.putAll(initialValues);
        }

        @Override
        public PlayerColorPreference load(UUID playerId) {
            return values.get(playerId);
        }

        @Override
        public void save(UUID playerId, PlayerColorPreference preference) {
            values.put(playerId, preference);
        }

        @Override
        public void remove(UUID playerId) {
            values.remove(playerId);
        }

        @Override
        public String description() {
            return description;
        }

        private Map<UUID, PlayerColorPreference> values() {
            return values;
        }
    }
}
