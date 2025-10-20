package net.democracycraft.democracyelections.util.bytebin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public final class BytebinClient {
    private static final String DEFAULT_SERVER = "https://bytebin.lucko.me";

    private BytebinClient() {}

    public static String uploadJson(String json) throws Exception {
        return uploadJson(json, null);
    }

    public static String uploadJson(String json, String serverBase) throws Exception {
        String base = (serverBase == null || serverBase.isBlank()) ? DEFAULT_SERVER : serverBase;
        if (base.endsWith("/")) base = base.substring(0, base.length()-1);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/post"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "DemocracyElections/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> resp = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() != 201 && resp.statusCode() != 200) {
            throw new IllegalStateException("Bytebin responded with status: " + resp.statusCode());
        }
        String location = Optional.ofNullable(resp.headers().firstValue("Location").orElse(null))
                .orElseThrow(() -> new IllegalStateException("Bytebin missing Location header"));
        if (!location.startsWith("/")) location = "/" + location;
        return base + location;
    }
}

