package net.democracycraft.democracyelections.util.bytebin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Simple client to upload JSON content to paste.gg and return a shareable URL.
 * <p>
 * This class preserves the existing public API used by the plugin (uploadJson),
 * but the implementation now targets paste.gg
 * </p>
 */
public final class ExportClient {
    /**
     * Default paste.gg API base endpoint.
     */
    private static final String DEFAULT_API = "https://api.paste.gg";

    /**
     * Default paste.gg viewing base URL prefix. The public paste URL is this prefix + paste id.
     */
    private static final String DEFAULT_VIEW_PREFIX = "https://paste.gg/p/anonymous/";

    private ExportClient() {}

    /**
     * Uploads the provided JSON string as a single-file paste to paste.gg and returns the public URL.
     *
     * @param json the JSON content to upload
     * @return the public paste.gg URL for the uploaded content
     * @throws Exception if the upload fails or the response cannot be parsed
     */
    public static String uploadJson(String json) throws Exception {
        return uploadJson(json, null);
    }

    /**
     * Uploads the provided JSON string as a single-file paste to paste.gg and returns the public URL.
     * <p>
     * The serverBase parameter, if provided, is treated as an override for the API base (e.g. "https://api.paste.gg").
     * The returned URL will still use the default viewing domain ("https://paste.gg/p/") combined with the paste id.
     * </p>
     *
     * @param json       the JSON content to upload
     * @param serverBase optional override for the paste.gg API base URL
     * @return the public paste.gg URL for the uploaded content
     * @throws Exception if the upload fails or the response cannot be parsed
     */
    public static String uploadJson(String json, String serverBase) throws Exception {
        String apiBase = (serverBase == null || serverBase.isBlank()) ? DEFAULT_API : trimTrailingSlash(serverBase);

        // Build paste.gg payload: single file with the JSON content as text.
        String payload = buildPayload(json);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/v1/pastes"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("User-Agent", "DemocracyElections/1.0 (+paste.gg)")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
        if (code != 201 && code != 200) {
            throw new IllegalStateException("paste.gg responded with status: " + code + (body.isEmpty() ? "" : ": " + truncate(body, 256)));
        }

        String id = extractPasteIdFromResponse(body);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Unable to parse paste id from paste.gg response");
        }
        return DEFAULT_VIEW_PREFIX + id;
    }

    // --- helpers ---

    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * Builds the minimal payload for paste.gg anonymous paste creation.
     */
    private static String buildPayload(String json) {
        // We avoid bringing a JSON library; escape minimal characters for a JSON string value.
        String escaped = escapeForJsonString(json);
        // Provide a stable file name and a short paste name.
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

    /**
     * Very small JSON string escaper for our controlled input (the JSON blob to upload).
     */
    private static String escapeForJsonString(String s) {
        if (s == null || s.isEmpty()) return "";
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

    /**
     * Extracts the paste id from the paste.gg response JSON without a full JSON parser.
     * We look for the first id within the top-level "result" object.
     */
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
