package net.democracycraft.elections.util.paste;

import net.democracycraft.elections.Elections;
import static net.democracycraft.elections.util.config.ConfigPaths.PASTEGG_API_BASE;
import static net.democracycraft.elections.util.config.ConfigPaths.PASTEGG_VIEW_BASE;
import static net.democracycraft.elections.util.config.ConfigPaths.PASTEGG_API_KEY;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * paste.gg client implementing the PasteStorage asynchronous contract.
 *
 * Features:
 * - Anonymous uploads (no API key) or authenticated (API key) to allow deletion.
 * - Sensible connect and request timeouts.
 * - All network I/O is executed off the main server thread; callbacks are invoked on the main thread.
 *
 * Configuration (config.yml):
 * - pastegg.apiBase   (default: https://api.paste.gg)
 * - pastegg.viewBase  (default: https://paste.gg/p/anonymous/)
 * - pastegg.apiKey    (optional)
 */
public class PasteGGClient implements PasteStorage {
    private final String apiBase;
    private final String viewBase;
    private final String apiKey; // may be null
    private final HttpClient client;

    /**
     * Creates a new client reading configuration from the plugin instance.
     */
    public PasteGGClient() {
        var plugin = Elections.getInstance();
        var cfg = plugin.getConfig();
        this.apiBase = trimTrailingSlash(cfg.getString(PASTEGG_API_BASE.getPath(), "https://api.paste.gg"));
        this.viewBase = ensureTrailingSlash(cfg.getString(PASTEGG_VIEW_BASE.getPath(), "https://paste.gg/p/anonymous/"));
        String key = cfg.getString(PASTEGG_API_KEY.getPath());
        this.apiKey = (key == null || key.isBlank()) ? null : key.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // --- ASYNC API ---
    /**
     * Uploads content to paste.gg asynchronously.
     *
     * Threading:
     * - The network operation runs on a Bukkit async task.
     * - onSuccess/onError are invoked on the Bukkit main thread.
     *
     * @param content content to upload (raw string; will be JSON-escaped internally)
     * @param onSuccess callback receiving the newly created paste id
     * @param onError callback receiving the thrown exception
     */
    @Override
    public void putAsync(String content, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        var plugin = Elections.getInstance();
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    String id = putBlocking(content);
                    new BukkitRunnable() {
                        @Override public void run() { if (onSuccess != null) onSuccess.accept(id); }
                    }.runTask(plugin);
                } catch (Throwable t) {
                    new BukkitRunnable() {
                        @Override public void run() { if (onError != null) onError.accept(t); }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Fetches a paste by id asynchronously.
     *
     * Threading:
     * - The network operation runs on a Bukkit async task.
     * - onSuccess/onError are invoked on the Bukkit main thread.
     *
     * @param id paste identifier
     * @param onSuccess callback receiving the response body, or null if 404
     * @param onError callback receiving the thrown exception
     */
    @Override
    public void getAsync(String id, Consumer<String> onSuccess, Consumer<Throwable> onError) {
        var plugin = Elections.getInstance();
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    String body = getBlocking(id);
                    new BukkitRunnable() {
                        @Override public void run() { if (onSuccess != null) onSuccess.accept(body); }
                    }.runTask(plugin);
                } catch (Throwable t) {
                    new BukkitRunnable() {
                        @Override public void run() { if (onError != null) onError.accept(t); }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Deletes a paste by id asynchronously.
     *
     * Semantics:
     * - onSuccess(true) if deleted successfully or already not found.
     * - onSuccess(false) if the API rejects the request (e.g., unauthorized).
     *
     * Threading:
     * - The network operation runs on a Bukkit async task.
     * - onSuccess/onError are invoked on the Bukkit main thread.
     *
     * @param id paste identifier
     * @param onSuccess callback receiving the boolean result
     * @param onError callback receiving the thrown exception
     */
    @Override
    public void deleteAsync(String id, Consumer<Boolean> onSuccess, Consumer<Throwable> onError) {
        var plugin = Elections.getInstance();
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    boolean ok = deleteBlocking(id);
                    new BukkitRunnable() {
                        @Override public void run() { if (onSuccess != null) onSuccess.accept(ok); }
                    }.runTask(plugin);
                } catch (Throwable t) {
                    new BukkitRunnable() {
                        @Override public void run() { if (onError != null) onError.accept(t); }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Builds the human-friendly paste.gg view URL for the given id.
     *
     * @param id paste identifier
     * @return absolute view URL
     */
    @Override
    public String viewUrl(String id) { return viewBase + id; }

    // --- blocking helpers (private) ---
    private String putBlocking(String content) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            Elections.getInstance().getLogger().warning("PasteGGClient.putBlocking invoked on main thread; this may freeze the tick.");
        }
        String payload = buildPayload(content);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/v1/pastes"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("User-Agent", "DemocracyElections/1.0 (+paste.gg)")
                .expectContinue(false)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (apiKey != null) b.header("Authorization", "Key " + apiKey);
        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
        if (code != 201 && code != 200) {
            throw new IllegalStateException("paste.gg responded with status: " + code + (body.isEmpty() ? "" : ": " + truncate(body, 256)));
        }
        String id = extractPasteIdFromResponse(body);
        if (id == null || id.isBlank()) throw new IllegalStateException("Unable to parse paste id from paste.gg response");
        return id;
    }

    private String getBlocking(String id) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            Elections.getInstance().getLogger().warning("PasteGGClient.getBlocking invoked on main thread.");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/v1/pastes/" + id))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DemocracyElections/1.0 (+paste.gg)")
                .GET();
        if (apiKey != null) b.header("Authorization", "Key " + apiKey);
        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 404) return null;
        if (resp.statusCode() != 200) throw new IllegalStateException("paste.gg GET status: " + resp.statusCode());
        return resp.body();
    }

    private boolean deleteBlocking(String id) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            Elections.getInstance().getLogger().warning("PasteGGClient.deleteBlocking invoked on main thread.");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/v1/pastes/" + id))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "DemocracyElections/1.0 (+paste.gg)")
                .DELETE();
        if (apiKey != null) b.header("Authorization", "Key " + apiKey);
        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code == 404) return true; // already gone
        if (code == 200 || code == 204) return true;
        if (code == 401 || code == 403) return false;
        throw new IllegalStateException("paste.gg DELETE status: " + code);
    }

    // --- helpers ---
    private static String ensureTrailingSlash(String s) {
        if (s == null || s.isBlank()) return "";
        return s.endsWith("/") ? s : (s + "/");
    }
    private static String trimTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length()-1) : s;
    }

    private static String buildPayload(String json) {
        String escaped = escapeForJsonString(json == null ? "" : json);
        return "{" +
                "\"name\":\"DemocracyElections export\"," +
                "\"files\":[{" +
                "\"name\":\"election.json\"," +
                "\"content\":{" +
                "\"format\":\"text\"," +
                "\"value\":\"" + escaped + "\"}" +
                "}]" +
                "}";
    }

    private static String escapeForJsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String extractPasteIdFromResponse(String body) {
        if (body == null || body.isEmpty()) return null;
        int resIdx = body.indexOf("\"result\"");
        if (resIdx < 0) return findFirstId(body);
        int searchFrom = body.indexOf('{', resIdx);
        if (searchFrom < 0) searchFrom = resIdx;
        int idKey = body.indexOf("\"id\"", searchFrom);
        if (idKey < 0) return findFirstId(body);
        int colon = body.indexOf(':', idKey);
        if (colon < 0) return null;
        int q1 = body.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2);
    }

    private static String findFirstId(String body) {
        int idKey = body.indexOf("\"id\"");
        if (idKey < 0) return null;
        int colon = body.indexOf(':', idKey);
        if (colon < 0) return null;
        int q1 = body.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return body.substring(q1 + 1, q2);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
