package ym.ymchat.service.color;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ym.ymchat.YmChatPlugin;
import ym.ymchat.service.crossserver.CrossServerChatService;

public final class PlayerColorPreferenceCacheRepository implements PlayerColorPreferenceRepository {

    private static final int LOCK_STRIPES = 64;

    private final PlayerColorPreferencePersistenceBackend localBackend;
    private final PlayerColorPreferencePersistenceBackend crossServerBackend;
    private final Executor asyncExecutor;
    private final Logger logger;
    private final Function<UUID, Player> playerLookup;
    private final Map<CacheKey, CachedPreferenceState> states = new ConcurrentHashMap<>();
    private final Object[] playerLocks = createLockStripes();
    private final AtomicLong mutationSequence = new AtomicLong();
    private final AtomicLong bindingGeneration = new AtomicLong();
    private volatile StorageMode storageMode = StorageMode.LOCAL;

    public PlayerColorPreferenceCacheRepository(
        YmChatPlugin plugin,
        PlayerColorPreferenceRepository localRepository,
        CrossServerChatService crossServerChatService
    ) {
        this(
            new LocalBackend(localRepository),
            new CrossServerBackend(crossServerChatService),
            task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task),
            plugin.getLogger(),
            Bukkit::getPlayer
        );
    }

    PlayerColorPreferenceCacheRepository(
        PlayerColorPreferencePersistenceBackend localBackend,
        PlayerColorPreferencePersistenceBackend crossServerBackend,
        Executor asyncExecutor,
        Logger logger,
        Function<UUID, Player> playerLookup
    ) {
        this.localBackend = Objects.requireNonNull(localBackend, "localBackend");
        this.crossServerBackend = Objects.requireNonNull(crossServerBackend, "crossServerBackend");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
    }

    public void rebind(boolean crossServerAvailable) {
        storageMode = crossServerAvailable ? StorageMode.CROSS_SERVER : StorageMode.LOCAL;
        bindingGeneration.incrementAndGet();
        states.clear();
    }

    public void preload(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (ColorScope scope : ColorScope.values()) {
            preload(playerId, scope);
        }
    }

    public void preload(UUID playerId, ColorScope scope) {
        if (playerId == null) {
            return;
        }
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long expectedVersion = currentVersion(playerId, effectiveScope);
        asyncExecutor.execute(() -> preloadBlocking(playerId, effectiveScope, mode, generation, expectedVersion));
    }

    public void preloadBlocking(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (ColorScope scope : ColorScope.values()) {
            preloadBlocking(playerId, scope);
        }
    }

    public void preloadBlocking(UUID playerId, ColorScope scope) {
        if (playerId == null) {
            return;
        }
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        preloadBlocking(playerId, effectiveScope, storageMode, bindingGeneration.get(), currentVersion(playerId, effectiveScope));
    }

    public void clearRuntime(UUID playerId) {
        if (playerId == null) {
            return;
        }
        for (ColorScope scope : ColorScope.values()) {
            CacheKey key = new CacheKey(playerId, scope);
            states.computeIfPresent(key, (ignored, state) -> state.withPreference(null));
        }
    }

    @Override
    public PlayerColorPreference get(UUID playerId, ColorScope scope) {
        CachedPreferenceState state = states.get(new CacheKey(playerId, scope == null ? ColorScope.CHAT : scope));
        return state == null ? null : state.preference();
    }

    @Override
    public void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) {
        if (playerId == null || preference == null) {
            return;
        }
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        CacheKey key = new CacheKey(playerId, effectiveScope);
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long version = mutationSequence.incrementAndGet();
        states.put(key, new CachedPreferenceState(version, preference, true));
        asyncExecutor.execute(() -> persistSave(key, preference, mode, generation, version));
    }

    @Override
    public void remove(UUID playerId, ColorScope scope) {
        if (playerId == null) {
            return;
        }
        ColorScope effectiveScope = scope == null ? ColorScope.CHAT : scope;
        CacheKey key = new CacheKey(playerId, effectiveScope);
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long version = mutationSequence.incrementAndGet();
        states.put(key, new CachedPreferenceState(version, null, true));
        asyncExecutor.execute(() -> persistRemove(key, mode, generation, version));
    }

    private void preloadBlocking(
        UUID playerId,
        ColorScope scope,
        StorageMode mode,
        long generation,
        long expectedVersion
    ) {
        PlayerColorPreferencePersistenceBackend backend = backend(mode);
        CacheKey key = new CacheKey(playerId, scope);
        try {
            PlayerColorPreference preference = backend.load(playerId, scope);
            states.compute(key, (ignored, current) -> {
                if (generation != bindingGeneration.get()) {
                    return current;
                }
                long currentVersion = current == null ? 0L : current.version();
                if (currentVersion != expectedVersion || (current != null && current.dirty())) {
                    return current;
                }
                return toState(currentVersion, preference, false);
            });
        } catch (Exception exception) {
            if (generation == bindingGeneration.get()) {
                logger.log(
                    Level.WARNING,
                    "Failed to preload player " + scope.storageKey() + " color preference from " + backend.description()
                        + " for " + playerId + ": " + exception.getMessage()
                );
            }
        }
    }

    private void persistSave(
        CacheKey key,
        PlayerColorPreference preference,
        StorageMode mode,
        long generation,
        long version
    ) {
        PlayerColorPreferencePersistenceBackend backend = backend(mode);
        synchronized (lockFor(key)) {
            if (!isCurrent(key, generation, version, mode, true)) {
                return;
            }
            try {
                backend.save(key.playerId(), key.scope(), preference);
            } catch (Exception exception) {
                logAsyncFailure("save", backend, key, generation, version, mode, exception);
                return;
            }
        }
        markPersisted(key, preference, generation, version);
    }

    private void persistRemove(CacheKey key, StorageMode mode, long generation, long version) {
        PlayerColorPreferencePersistenceBackend backend = backend(mode);
        synchronized (lockFor(key)) {
            if (!isCurrent(key, generation, version, mode, true)) {
                return;
            }
            try {
                backend.remove(key.playerId(), key.scope());
            } catch (Exception exception) {
                logAsyncFailure("remove", backend, key, generation, version, mode, exception);
                return;
            }
        }
        markPersisted(key, null, generation, version);
    }

    private void markPersisted(CacheKey key, PlayerColorPreference preference, long generation, long version) {
        states.compute(key, (ignored, current) -> {
            if (generation != bindingGeneration.get()) {
                return current;
            }
            if (current == null || current.version() != version) {
                return current;
            }
            PlayerColorPreference effectivePreference = current.preference();
            Player player = playerLookup.apply(key.playerId());
            if (player != null && player.isOnline()) {
                effectivePreference = preference;
            }
            return toState(version, effectivePreference, false);
        });
    }

    private boolean isCurrent(
        CacheKey key,
        long generation,
        long version,
        StorageMode expectedMode,
        boolean expectedDirty
    ) {
        if (generation != bindingGeneration.get() || storageMode != expectedMode) {
            return false;
        }
        CachedPreferenceState state = states.get(key);
        return state != null && state.version() == version && state.dirty() == expectedDirty;
    }

    private void logAsyncFailure(
        String action,
        PlayerColorPreferencePersistenceBackend backend,
        CacheKey key,
        long generation,
        long version,
        StorageMode mode,
        Exception exception
    ) {
        if (isCurrent(key, generation, version, mode, true)) {
            logger.log(
                Level.WARNING,
                "Failed to " + action + " player " + key.scope().storageKey() + " color preference in "
                    + backend.description() + " for " + key.playerId() + ": " + exception.getMessage()
            );
        }
    }

    private PlayerColorPreferencePersistenceBackend backend(StorageMode mode) {
        return mode == StorageMode.CROSS_SERVER ? crossServerBackend : localBackend;
    }

    private long currentVersion(UUID playerId, ColorScope scope) {
        CachedPreferenceState state = states.get(new CacheKey(playerId, scope));
        return state == null ? 0L : state.version();
    }

    private CachedPreferenceState toState(long version, PlayerColorPreference preference, boolean dirty) {
        if (version <= 0L && preference == null && !dirty) {
            return null;
        }
        return new CachedPreferenceState(version, preference, dirty);
    }

    private Object lockFor(CacheKey key) {
        return playerLocks[Math.floorMod(key.hashCode(), playerLocks.length)];
    }

    private static Object[] createLockStripes() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private enum StorageMode {
        LOCAL,
        CROSS_SERVER
    }

    private record CacheKey(UUID playerId, ColorScope scope) {

        private CacheKey {
            scope = scope == null ? ColorScope.CHAT : scope;
        }
    }

    private record CachedPreferenceState(long version, PlayerColorPreference preference, boolean dirty) {

        private CachedPreferenceState withPreference(PlayerColorPreference preference) {
            return new CachedPreferenceState(version, preference, dirty);
        }
    }

    private static final class LocalBackend implements PlayerColorPreferencePersistenceBackend {

        private final PlayerColorPreferenceRepository repository;

        private LocalBackend(PlayerColorPreferenceRepository repository) {
            this.repository = repository;
        }

        @Override
        public PlayerColorPreference load(UUID playerId, ColorScope scope) {
            return repository.get(playerId, scope);
        }

        @Override
        public void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) {
            repository.save(playerId, scope, preference);
        }

        @Override
        public void remove(UUID playerId, ColorScope scope) {
            repository.remove(playerId, scope);
        }

        @Override
        public String description() {
            return "player-colors.yml";
        }
    }

    private static final class CrossServerBackend implements PlayerColorPreferencePersistenceBackend {

        private final CrossServerChatService service;

        private CrossServerBackend(CrossServerChatService service) {
            this.service = service;
        }

        @Override
        public PlayerColorPreference load(UUID playerId, ColorScope scope) throws Exception {
            return service.findPlayerColorPreference(playerId, scope);
        }

        @Override
        public void save(UUID playerId, ColorScope scope, PlayerColorPreference preference) throws Exception {
            service.savePlayerColorPreference(playerId, scope, preference);
        }

        @Override
        public void remove(UUID playerId, ColorScope scope) throws Exception {
            service.removePlayerColorPreference(playerId, scope);
        }

        @Override
        public String description() {
            return "cross-server PostgreSQL";
        }
    }
}
