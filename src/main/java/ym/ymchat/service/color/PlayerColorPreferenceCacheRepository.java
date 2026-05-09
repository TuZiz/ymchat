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

    private final PlayerColorPreferencePersistenceBackend localBackend;
    private final PlayerColorPreferencePersistenceBackend crossServerBackend;
    private final Executor asyncExecutor;
    private final Logger logger;
    private final Function<UUID, Player> playerLookup;
    private final Map<UUID, CachedPreferenceState> states = new ConcurrentHashMap<>();
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
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
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long expectedVersion = currentVersion(playerId);
        asyncExecutor.execute(() -> preloadBlocking(playerId, mode, generation, expectedVersion));
    }

    public void preloadBlocking(UUID playerId) {
        if (playerId == null) {
            return;
        }
        preloadBlocking(playerId, storageMode, bindingGeneration.get(), currentVersion(playerId));
    }

    public void clearRuntime(UUID playerId) {
        if (playerId == null) {
            return;
        }
        states.computeIfPresent(playerId, (ignored, state) -> state.withPreference(null));
    }

    @Override
    public PlayerColorPreference get(UUID playerId) {
        CachedPreferenceState state = states.get(playerId);
        return state == null ? null : state.preference();
    }

    @Override
    public void save(UUID playerId, PlayerColorPreference preference) {
        if (playerId == null || preference == null) {
            return;
        }
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long version = mutationSequence.incrementAndGet();
        states.put(playerId, new CachedPreferenceState(version, preference, mode == StorageMode.CROSS_SERVER));
        if (mode == StorageMode.LOCAL) {
            persistLocalSave(playerId, preference, generation, version);
            return;
        }
        asyncExecutor.execute(() -> persistCrossServerSave(playerId, preference, generation, version));
    }

    @Override
    public void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }
        StorageMode mode = storageMode;
        long generation = bindingGeneration.get();
        long version = mutationSequence.incrementAndGet();
        states.put(playerId, new CachedPreferenceState(version, null, mode == StorageMode.CROSS_SERVER));
        if (mode == StorageMode.LOCAL) {
            persistLocalRemove(playerId, generation, version);
            return;
        }
        asyncExecutor.execute(() -> persistCrossServerRemove(playerId, generation, version));
    }

    private void preloadBlocking(UUID playerId, StorageMode mode, long generation, long expectedVersion) {
        PlayerColorPreferencePersistenceBackend backend = backend(mode);
        try {
            PlayerColorPreference preference = backend.load(playerId);
            states.compute(playerId, (ignored, current) -> {
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
                    "Failed to preload player color preference from " + backend.description()
                        + " for " + playerId + ": " + exception.getMessage()
                );
            }
        }
    }

    private void persistLocalSave(UUID playerId, PlayerColorPreference preference, long generation, long version) {
        if (!isCurrent(playerId, generation, version, StorageMode.LOCAL, false)) {
            return;
        }
        try {
            localBackend.save(playerId, preference);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save player color preference to " + localBackend.description(), exception);
        }
    }

    private void persistLocalRemove(UUID playerId, long generation, long version) {
        if (!isCurrent(playerId, generation, version, StorageMode.LOCAL, false)) {
            return;
        }
        try {
            localBackend.remove(playerId);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to remove player color preference from " + localBackend.description(), exception);
        }
    }

    private void persistCrossServerSave(UUID playerId, PlayerColorPreference preference, long generation, long version) {
        synchronized (lockFor(playerId)) {
            if (!isCurrent(playerId, generation, version, StorageMode.CROSS_SERVER, true)) {
                return;
            }
            try {
                crossServerBackend.save(playerId, preference);
            } catch (Exception exception) {
                logAsyncFailure("save", crossServerBackend, playerId, generation, version, exception);
                return;
            }
        }
        markPersisted(playerId, preference, generation, version);
    }

    private void persistCrossServerRemove(UUID playerId, long generation, long version) {
        synchronized (lockFor(playerId)) {
            if (!isCurrent(playerId, generation, version, StorageMode.CROSS_SERVER, true)) {
                return;
            }
            try {
                crossServerBackend.remove(playerId);
            } catch (Exception exception) {
                logAsyncFailure("remove", crossServerBackend, playerId, generation, version, exception);
                return;
            }
        }
        markPersisted(playerId, null, generation, version);
    }

    private void markPersisted(UUID playerId, PlayerColorPreference preference, long generation, long version) {
        states.compute(playerId, (ignored, current) -> {
            if (generation != bindingGeneration.get()) {
                return current;
            }
            if (current == null || current.version() != version) {
                return current;
            }
            PlayerColorPreference effectivePreference = current.preference();
            Player player = playerLookup.apply(playerId);
            if (player != null && player.isOnline()) {
                effectivePreference = preference;
            }
            return toState(version, effectivePreference, false);
        });
    }

    private boolean isCurrent(UUID playerId, long generation, long version, StorageMode expectedMode, boolean expectedDirty) {
        if (generation != bindingGeneration.get() || storageMode != expectedMode) {
            return false;
        }
        CachedPreferenceState state = states.get(playerId);
        return state != null && state.version() == version && state.dirty() == expectedDirty;
    }

    private void logAsyncFailure(
        String action,
        PlayerColorPreferencePersistenceBackend backend,
        UUID playerId,
        long generation,
        long version,
        Exception exception
    ) {
        if (isCurrent(playerId, generation, version, StorageMode.CROSS_SERVER, true)) {
            logger.log(
                Level.WARNING,
                "Failed to " + action + " player color preference in " + backend.description()
                    + " for " + playerId + ": " + exception.getMessage()
            );
        }
    }

    private PlayerColorPreferencePersistenceBackend backend(StorageMode mode) {
        return mode == StorageMode.CROSS_SERVER ? crossServerBackend : localBackend;
    }

    private long currentVersion(UUID playerId) {
        CachedPreferenceState state = states.get(playerId);
        return state == null ? 0L : state.version();
    }

    private CachedPreferenceState toState(long version, PlayerColorPreference preference, boolean dirty) {
        if (version <= 0L && preference == null && !dirty) {
            return null;
        }
        return new CachedPreferenceState(version, preference, dirty);
    }

    private Object lockFor(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    private enum StorageMode {
        LOCAL,
        CROSS_SERVER
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
        public PlayerColorPreference load(UUID playerId) {
            return repository.get(playerId);
        }

        @Override
        public void save(UUID playerId, PlayerColorPreference preference) {
            repository.save(playerId, preference);
        }

        @Override
        public void remove(UUID playerId) {
            repository.remove(playerId);
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
        public PlayerColorPreference load(UUID playerId) throws Exception {
            return service.findPlayerColorPreference(playerId);
        }

        @Override
        public void save(UUID playerId, PlayerColorPreference preference) throws Exception {
            service.savePlayerColorPreference(playerId, preference);
        }

        @Override
        public void remove(UUID playerId) throws Exception {
            service.removePlayerColorPreference(playerId);
        }

        @Override
        public String description() {
            return "cross-server PostgreSQL";
        }
    }
}
