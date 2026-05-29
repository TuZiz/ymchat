package ym.ymchat.service.chat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.plugin.Plugin;
import ym.ymchat.config.filter.FilterCloudSettings;

public final class FilterCloudWordService implements AutoCloseable {

    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "YmChat-FilterCloud");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .executor(executor)
        .build();
    private final CopyOnWriteArrayList<String> words = new CopyOnWriteArrayList<>();

    private volatile String currentUrl = "";
    private volatile long lastRefreshAt;

    public FilterCloudWordService(Plugin plugin) {
        this.plugin = plugin;
    }

    FilterCloudWordService(Plugin plugin, List<String> initialWords) {
        this(plugin);
        if (initialWords != null) {
            words.addAll(initialWords);
        }
        lastRefreshAt = System.currentTimeMillis();
    }

    public List<String> words(FilterCloudSettings settings) {
        if (settings == null || !settings.enabled() || settings.url() == null || settings.url().isBlank()) {
            return List.of();
        }
        refreshIfNeeded(settings);
        return words;
    }

    public void reload(FilterCloudSettings settings) {
        if (settings == null || !settings.enabled() || settings.url() == null || settings.url().isBlank()) {
            words.clear();
            currentUrl = "";
            lastRefreshAt = 0L;
            return;
        }

        if (!settings.url().equals(currentUrl)) {
            words.clear();
            currentUrl = settings.url();
            lastRefreshAt = 0L;
        }
        refreshIfNeeded(settings);
    }

    private void refreshIfNeeded(FilterCloudSettings settings) {
        long now = System.currentTimeMillis();
        long intervalMillis = Math.max(1L, settings.refreshMinutes()) * 60_000L;
        if (now - lastRefreshAt < intervalMillis) {
            return;
        }
        lastRefreshAt = now;
        CompletableFuture.runAsync(() -> refresh(settings), executor);
    }

    private void refresh(FilterCloudSettings settings) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.url()))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logWarning("Failed to refresh filter cloud words, HTTP " + response.statusCode() + ": " + settings.url());
                return;
            }
            String body = response.body();
            if (body.length() > MAX_RESPONSE_BYTES) {
                logWarning("Filter cloud response is too large, ignored: " + settings.url());
                return;
            }

            List<String> parsed = parseStringArray(body, settings.arrayPath());
            words.clear();
            words.addAll(parsed);
            if (plugin != null) {
                plugin.getLogger().info("Loaded " + parsed.size() + " cloud filter words.");
            }
        } catch (IllegalArgumentException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logWarning("Failed to refresh filter cloud words: " + exception.getMessage());
        }
    }

    static List<String> parseStringArray(String json, String arrayPath) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String key = arrayPath == null || arrayPath.isBlank() ? "words" : arrayPath;
        int keyIndex = json.indexOf('"' + escapeJsonKey(key) + '"');
        if (keyIndex < 0) {
            return List.of();
        }
        int colon = json.indexOf(':', keyIndex);
        int arrayStart = json.indexOf('[', colon);
        if (colon < 0 || arrayStart < 0) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int index = arrayStart + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (!inString && character == ']') {
                break;
            }
            if (!inString) {
                if (character == '"') {
                    inString = true;
                    current.setLength(0);
                }
                continue;
            }
            if (escaping) {
                appendEscaped(current, character);
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = true;
                continue;
            }
            if (character == '"') {
                String value = current.toString().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
                inString = false;
                continue;
            }
            current.append(character);
        }
        return List.copyOf(result);
    }

    private static String escapeJsonKey(String key) {
        return key.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void appendEscaped(StringBuilder builder, char character) {
        switch (character) {
            case 'n' -> builder.append('\n');
            case 'r' -> builder.append('\r');
            case 't' -> builder.append('\t');
            case 'b' -> builder.append('\b');
            case 'f' -> builder.append('\f');
            default -> builder.append(character);
        }
    }

    private void logWarning(String message) {
        if (plugin != null) {
            plugin.getLogger().warning(message);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
