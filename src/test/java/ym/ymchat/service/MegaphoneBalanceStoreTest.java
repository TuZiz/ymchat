package ym.ymchat.service.megaphone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class MegaphoneBalanceStoreTest {

    @Test
    void usesLocalBackendSynchronouslyWhenCrossServerIsDisabled() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local", Map.of(playerId, 3));
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, 9));
        QueueingExecutor executor = new QueueingExecutor();
        MegaphoneBalanceStore store = new MegaphoneBalanceStore(
            localBackend,
            crossServerBackend,
            executor,
            Logger.getLogger("MegaphoneBalanceStoreTest")
        );

        store.reload("megaphones.yml", false);
        int updated = store.give(fakeOfflinePlayer(playerId, "Tu_zi", true), 2).join();

        assertEquals(5, updated);
        assertEquals(5, store.balance(playerId));
        assertEquals(5, localBackend.values().get(playerId));
        assertEquals(0, executor.size());
        assertEquals(9, crossServerBackend.values().get(playerId));
    }

    @Test
    void usesCrossServerBackendWhenEnabled() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, 7));
        QueueingExecutor executor = new QueueingExecutor();
        MegaphoneBalanceStore store = new MegaphoneBalanceStore(
            localBackend,
            crossServerBackend,
            executor,
            Logger.getLogger("MegaphoneBalanceStoreTest")
        );

        store.reload("megaphones.yml", true);
        store.preloadBlocking(playerId);
        var future = store.consume(fakeOfflinePlayer(playerId, "Tu_zi", true), 2);
        executor.runAll();
        MegaphoneBalanceStore.ConsumeResult result = future.join();

        assertTrue(result.allowed());
        assertEquals(5, result.balance());
        assertEquals(5, store.balance(playerId));
        assertEquals(5, crossServerBackend.values().get(playerId));
        assertFalse(localBackend.values().containsKey(playerId));
    }

    @Test
    void offlineBalanceQueryLoadsCrossServerValueWhenCacheIsEmpty() {
        UUID playerId = UUID.randomUUID();
        InMemoryBackend localBackend = new InMemoryBackend("local");
        InMemoryBackend crossServerBackend = new InMemoryBackend("cross-server", Map.of(playerId, 4));
        QueueingExecutor executor = new QueueingExecutor();
        MegaphoneBalanceStore store = new MegaphoneBalanceStore(
            localBackend,
            crossServerBackend,
            executor,
            Logger.getLogger("MegaphoneBalanceStoreTest")
        );

        store.reload("megaphones.yml", true);
        var future = store.queryBalance(fakeOfflinePlayer(playerId, "Tu_zi", false));
        executor.runAll();

        assertEquals(4, future.join());
        assertEquals(4, store.balance(playerId));
    }

    private OfflinePlayer fakeOfflinePlayer(UUID playerId, String name, boolean online) {
        return (OfflinePlayer) Proxy.newProxyInstance(
            OfflinePlayer.class.getClassLoader(),
            new Class<?>[]{OfflinePlayer.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getName" -> name;
                case "isOnline" -> online;
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

        private int size() {
            return tasks.size();
        }
    }

    private static final class InMemoryBackend implements MegaphoneBalancePersistenceBackend {

        private final String description;
        private final Map<UUID, Integer> values = new HashMap<>();

        private InMemoryBackend(String description) {
            this(description, Map.of());
        }

        private InMemoryBackend(String description, Map<UUID, Integer> initialValues) {
            this.description = description;
            this.values.putAll(initialValues);
        }

        @Override
        public int load(UUID playerId) {
            return values.getOrDefault(playerId, 0);
        }

        @Override
        public Map<UUID, Integer> snapshotBalances() {
            return Map.copyOf(values);
        }

        @Override
        public int adjust(UUID playerId, String playerName, int delta) {
            int updated = Math.max(0, load(playerId) + delta);
            values.put(playerId, updated);
            return updated;
        }

        @Override
        public MegaphoneBalanceStore.ConsumeResult consume(UUID playerId, String playerName, int amount) {
            int current = load(playerId);
            if (current < amount) {
                return new MegaphoneBalanceStore.ConsumeResult(false, current);
            }
            int updated = current - amount;
            values.put(playerId, updated);
            return new MegaphoneBalanceStore.ConsumeResult(true, updated);
        }

        @Override
        public String description() {
            return description;
        }

        private Map<UUID, Integer> values() {
            return values;
        }
    }
}
