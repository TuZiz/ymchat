package ym.ymchat.service.megaphone;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.ymchat.YmChatPlugin;

import ym.ymchat.service.crossserver.CrossServerChatService;
public final class MegaphoneBalanceStore {

    private final MegaphoneBalancePersistenceBackend localBackend;
    private final MegaphoneBalancePersistenceBackend crossServerBackend;
    private final Executor asyncExecutor;
    private final Logger logger;
    private final ConcurrentMap<UUID, Integer> balances = new ConcurrentHashMap<>();
    private volatile StorageMode storageMode = StorageMode.LOCAL;

    public MegaphoneBalanceStore(YmChatPlugin plugin, CrossServerChatService crossServerChatService) {
        this(
            new LocalBackend(plugin),
            new CrossServerBackend(crossServerChatService),
            task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task),
            plugin.getLogger()
        );
    }

    MegaphoneBalanceStore(
        MegaphoneBalancePersistenceBackend localBackend,
        MegaphoneBalancePersistenceBackend crossServerBackend,
        Executor asyncExecutor,
        Logger logger
    ) {
        this.localBackend = localBackend;
        this.crossServerBackend = crossServerBackend;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    public synchronized void reload(String fileName, boolean crossServerAvailable) {
        try {
            localBackend.reload(fileName);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to reload local megaphone balance storage: " + exception.getMessage());
        }

        storageMode = crossServerAvailable ? StorageMode.CROSS_SERVER : StorageMode.LOCAL;
        balances.clear();
        if (storageMode != StorageMode.LOCAL) {
            return;
        }
        try {
            balances.putAll(localBackend.snapshotBalances());
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to load local megaphone balances: " + exception.getMessage());
        }
    }

    public boolean isCrossServerEnabled() {
        return storageMode == StorageMode.CROSS_SERVER;
    }

    public void preload(UUID playerId) {
        if (playerId == null || storageMode != StorageMode.CROSS_SERVER) {
            return;
        }
        asyncExecutor.execute(() -> loadIntoCache(playerId));
    }

    public void preloadBlocking(UUID playerId) {
        if (playerId == null || storageMode != StorageMode.CROSS_SERVER) {
            return;
        }
        loadIntoCache(playerId);
    }

    public void clearRuntime(UUID playerId) {
        if (playerId == null || storageMode != StorageMode.CROSS_SERVER) {
            return;
        }
        balances.remove(playerId);
    }

    public int balance(UUID uuid) {
        return uuid == null ? 0 : balances.getOrDefault(uuid, 0);
    }

    public CompletableFuture<Integer> queryBalance(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID playerId = player.getUniqueId();
        if (storageMode == StorageMode.LOCAL) {
            return CompletableFuture.completedFuture(balance(playerId));
        }
        if (player.isOnline() && balances.containsKey(playerId)) {
            return CompletableFuture.completedFuture(balance(playerId));
        }
        return supplyAsync(() -> {
            int loaded = crossServerBackend.load(playerId);
            balances.put(playerId, loaded);
            return loaded;
        });
    }

    public CompletableFuture<Integer> give(OfflinePlayer player, int amount) {
        return adjust(player, Math.max(0, amount));
    }

    public CompletableFuture<Integer> take(OfflinePlayer player, int amount) {
        return adjust(player, -Math.max(0, amount));
    }

    public CompletableFuture<ConsumeResult> consume(OfflinePlayer player, int amount) {
        if (player == null || player.getUniqueId() == null) {
            return CompletableFuture.completedFuture(new ConsumeResult(false, 0));
        }
        int cost = Math.max(0, amount);
        UUID playerId = player.getUniqueId();
        String playerName = playerName(player);
        if (storageMode == StorageMode.LOCAL) {
            try {
                ConsumeResult result = localBackend.consume(playerId, playerName, cost);
                balances.put(playerId, result.balance());
                return CompletableFuture.completedFuture(result);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        return supplyAsync(() -> {
            ConsumeResult result = crossServerBackend.consume(playerId, playerName, cost);
            balances.put(playerId, result.balance());
            return result;
        });
    }

    private CompletableFuture<Integer> adjust(OfflinePlayer player, int delta) {
        if (player == null || player.getUniqueId() == null) {
            return CompletableFuture.completedFuture(0);
        }
        UUID playerId = player.getUniqueId();
        String playerName = playerName(player);
        if (storageMode == StorageMode.LOCAL) {
            try {
                int updated = localBackend.adjust(playerId, playerName, delta);
                balances.put(playerId, updated);
                return CompletableFuture.completedFuture(updated);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        return supplyAsync(() -> {
            int updated = crossServerBackend.adjust(playerId, playerName, delta);
            balances.put(playerId, updated);
            return updated;
        });
    }

    private void loadIntoCache(UUID playerId) {
        try {
            balances.put(playerId, crossServerBackend.load(playerId));
        } catch (Exception exception) {
            logger.log(
                Level.WARNING,
                "Failed to preload megaphone balance from " + crossServerBackend.description()
                    + " for " + playerId + ": " + exception.getMessage()
            );
        }
    }

    private <T> CompletableFuture<T> supplyAsync(AsyncSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        asyncExecutor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private String playerName(OfflinePlayer player) {
        String name = player == null ? null : player.getName();
        return name == null || name.isBlank() ? "" : name;
    }

    public record ConsumeResult(boolean allowed, int balance) {
    }

    @FunctionalInterface
    private interface AsyncSupplier<T> {

        T get() throws Exception;
    }

    private enum StorageMode {
        LOCAL,
        CROSS_SERVER
    }

    private static final class LocalBackend implements MegaphoneBalancePersistenceBackend {

        private final YmChatPlugin plugin;
        private final ConcurrentMap<UUID, BalanceEntry> balances = new ConcurrentHashMap<>();
        private File file;

        private LocalBackend(YmChatPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public synchronized void reload(String fileName) {
            file = new File(plugin.getDataFolder(), sanitizeFileName(fileName));
            balances.clear();
            if (!file.isFile()) {
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection players = yaml.getConfigurationSection("Players");
            if (players == null) {
                return;
            }
            for (String key : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int amount = Math.max(0, players.getInt(key + ".Amount", 0));
                    String name = normalizeName(players.getString(key + ".Name", ""));
                    balances.put(uuid, new BalanceEntry(name, amount));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid megaphone player UUID: " + key);
                }
            }
        }

        @Override
        public synchronized int load(UUID playerId) {
            BalanceEntry entry = balances.get(playerId);
            return entry == null ? 0 : entry.amount();
        }

        @Override
        public synchronized Map<UUID, Integer> snapshotBalances() {
            Map<UUID, Integer> snapshot = new ConcurrentHashMap<>();
            for (Map.Entry<UUID, BalanceEntry> entry : balances.entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue().amount());
            }
            return Map.copyOf(snapshot);
        }

        @Override
        public synchronized int adjust(UUID playerId, String playerName, int delta) {
            int updated = Math.max(0, load(playerId) + delta);
            set(playerId, playerName, updated);
            return updated;
        }

        @Override
        public synchronized ConsumeResult consume(UUID playerId, String playerName, int amount) {
            int cost = Math.max(0, amount);
            int current = load(playerId);
            if (current < cost) {
                return new ConsumeResult(false, current);
            }
            int updated = current - cost;
            set(playerId, playerName, updated);
            return new ConsumeResult(true, updated);
        }

        @Override
        public String description() {
            return "megaphones.yml";
        }

        private void set(UUID playerId, String playerName, int amount) {
            balances.put(playerId, new BalanceEntry(normalizeName(playerName), Math.max(0, amount)));
            save();
        }

        private void save() {
            if (file == null) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                plugin.getLogger().warning("Failed to create megaphone data folder: " + parent);
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, BalanceEntry> entry : balances.entrySet()) {
                String path = "Players." + entry.getKey();
                yaml.set(path + ".Name", entry.getValue().name());
                yaml.set(path + ".Amount", entry.getValue().amount());
            }
            try {
                yaml.save(file);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to save megaphone data: " + exception.getMessage());
            }
        }

        private String sanitizeFileName(String fileName) {
            String normalized = fileName == null || fileName.isBlank() ? "megaphones.yml" : fileName.trim();
            normalized = normalized.replace('\\', '/');
            if (normalized.contains("..") || normalized.startsWith("/")) {
                return "megaphones.yml";
            }
            if (!normalized.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                return normalized + ".yml";
            }
            return normalized;
        }

        private String normalizeName(String playerName) {
            return playerName == null || playerName.isBlank() ? "" : playerName;
        }
    }

    private static final class CrossServerBackend implements MegaphoneBalancePersistenceBackend {

        private final CrossServerChatService service;

        private CrossServerBackend(CrossServerChatService service) {
            this.service = service;
        }

        @Override
        public int load(UUID playerId) throws Exception {
            return service.findMegaphoneBalance(playerId);
        }

        @Override
        public Map<UUID, Integer> snapshotBalances() {
            return Map.of();
        }

        @Override
        public int adjust(UUID playerId, String playerName, int delta) throws Exception {
            return service.adjustMegaphoneBalance(playerId, playerName, delta);
        }

        @Override
        public ConsumeResult consume(UUID playerId, String playerName, int amount) throws Exception {
            CrossServerChatService.MegaphoneBalanceResult result = service.consumeMegaphoneBalance(playerId, playerName, amount);
            return new ConsumeResult(result.allowed(), result.balance());
        }

        @Override
        public String description() {
            return "cross-server PostgreSQL";
        }
    }

    private record BalanceEntry(String name, int amount) {
    }
}
