package net.democracycraft.elections.src.util.export.github;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.GitHubGistService;
import net.democracycraft.elections.src.util.config.DataFolder;
import net.democracycraft.elections.src.util.yml.AutoYML;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link GitHubGistService} that uploads data to GitHub Gists via the REST API.
 *
 * <p>This client utilizes {@link HttpClient} for network operations and ensures that all
 * blocking I/O is performed off the main Bukkit thread. The configuration is managed
 * via {@link AutoYML} and loaded from the {@link DataFolder#GITHUB} directory.</p>
 *
 * <p><b>Note:</b> This implementation requires the server environment to have access
 * to the Google Gson library (standard in Spigot/Paper).</p>
 */
public class GitHubGistClient implements GitHubGistService {

    private final GitHubGistConfig config;
    private final HttpClient client;

    /**
     * Constructs a new {@code GitHubGistClient}.
     *
     * <p>This constructor loads the configuration from disk immediately.
     * If the configuration file does not exist, it will be created.</p>
     */
    public GitHubGistClient() {
        this.config = loadConfig();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Loads the GitHub Gist configuration from the plugin data folder.
     *
     * @return the loaded {@link GitHubGistConfig}
     */
    public static GitHubGistConfig loadConfig() {
        return AutoYML.create(
                GitHubGistConfig.class,
                "github-gist",
                DataFolder.GITHUB,
                "GitHub Gist export configuration.\n" +
                        "The personalAccessToken must belong to the account that will own the gists.\n" +
                        "Never share this file with untrusted users."
        ).loadOrCreate(GitHubGistConfig::new);
    }

    /**
     * Publishes the given payload to a new secret GitHub Gist.
     *
     * <p>The operation is scheduled asynchronously on the Bukkit worker thread pool to prevent
     * freezing the main server thread. The returned {@link CompletableFuture} will complete
     * with the URL of the created Gist upon success.</p>
     *
     * @param fileName    the name of the file within the Gist (e.g., "results.json")
     * @param jsonPayload the content of the file to be uploaded
     * @return a {@link CompletableFuture} containing the HTML URL of the published Gist
     */
    @Override
    public CompletableFuture<String> publish(@Nullable String fileName, @Nullable String jsonPayload) {
        if (fileName == null || jsonPayload == null) {
            return publish(Map.of());
        }
        return publish(Map.of(fileName, jsonPayload));
    }

    /**
     * Publishes the given payload to a new secret GitHub Gist with optional Markdown support.
     *
     * <p>The operation is scheduled asynchronously on the Bukkit worker thread pool to prevent
     * freezing the main server thread. The returned {@link CompletableFuture} will complete
     * with the URL of the created Gist upon success.</p>
     *
     * @param jsonFileName    the name of the JSON file within the Gist (e.g., "results.json")
     * @param jsonContent      the content of the JSON file to be uploaded
     * @param markdownFileName the name of the Markdown file within the Gist (optional)
     * @param markdownContent   the content of the Markdown file to be uploaded (optional)
     * @return a {@link CompletableFuture} containing the HTML URL of the published Gist
     */
    public CompletableFuture<String> publish(@Nullable String jsonFileName,
                                             @Nullable String jsonContent,
                                             @Nullable String markdownFileName,
                                             @Nullable String markdownContent) {
        JsonObject files = new JsonObject();
        if (jsonFileName != null && jsonContent != null) {
            files.addProperty(jsonFileName, jsonContent);
        } else if (jsonContent != null) {
            files.addProperty("election_data.json", jsonContent);
        }

        if (markdownFileName != null && markdownContent != null) {
            files.addProperty(markdownFileName, markdownContent);
        } else if (markdownContent != null) {
            String base = (jsonFileName == null || jsonFileName.isBlank()) ? "election_data" : jsonFileName;
            if (base.endsWith(".json")) base = base.substring(0, base.length() - 5);
            files.addProperty(base + ".md", markdownContent);
        }

        // Convert JsonObject to Map<String, String>
        Map<String, String> fileMap = new HashMap<>();
        files.entrySet().forEach(entry -> fileMap.put(entry.getKey(), entry.getValue().getAsString()));

        return publish(fileMap);
    }

    /**
     * Publishes multiple files to a new secret GitHub Gist.
     *
     * @param files Map of filename to content.
     * @return a {@link CompletableFuture} containing the HTML URL of the published Gist
     */
    public CompletableFuture<String> publish(Map<String, String> files) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Elections plugin = Elections.getInstance();

        if (!config.enabled) {
            future.completeExceptionally(new IllegalStateException("GitHub Gist export is disabled in configuration."));
            return future;
        }

        if (config.personalAccessToken == null || config.personalAccessToken.isBlank()) {
            future.completeExceptionally(new IllegalStateException("GitHub Gist personalAccessToken is not configured."));
            return future;
        }

        if (files == null || files.isEmpty()) {
            future.completeExceptionally(new IllegalArgumentException("No files provided for export."));
            return future;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String url = publishBlocking(files);
                    future.complete(url);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Performs the blocking HTTP request to GitHub.
     *
     * @param filesContent Map of filename to content.
     * @return the HTML URL of the created gist
     * @throws Exception if the request fails or the API returns a non-201 status
     */
    private String publishBlocking(Map<String, String> filesContent) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            Elections.getInstance().getLogger().warning("GitHubGistClient#publishBlocking invoked on the main thread! This causes server lag.");
        }

        JsonObject root = getJsonObject(filesContent);

        // Prepare the request
        String apiBase = (config.apiBase == null || config.apiBase.isBlank())
                ? "https://api.github.com"
                : config.apiBase.trim().replaceAll("/$", "");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/gists"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DemocracyElections/1.0")
                .header("Authorization", "Bearer " + config.personalAccessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                .build();

        // Send request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();

        if (status != 201) {
            throw new IllegalStateException("GitHub API returned error status: " + status + " | Body: " + response.body());
        }

        // Parse response
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        if (responseJson.has("html_url")) {
            return responseJson.get("html_url").getAsString();
        } else {
            throw new IllegalStateException("GitHub response missing 'html_url' field.");
        }
    }

    private @NotNull JsonObject getJsonObject(Map<String, String> filesContent) {
        JsonObject root = new JsonObject();
        root.addProperty("description", "Election data exported from DemocracyElections");
        root.addProperty("public", config.publicGists);

        JsonObject files = new JsonObject();

        for (Map.Entry<String, String> entry : filesContent.entrySet()) {
            // Skip empty content to avoid GitHub API 422 error
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            JsonObject fileContent = new JsonObject();
            fileContent.addProperty("content", entry.getValue());
            files.add(entry.getKey(), fileContent);
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("Cannot publish Gist: all provided files are empty.");
        }

        root.add("files", files);
        return root;
    }
}