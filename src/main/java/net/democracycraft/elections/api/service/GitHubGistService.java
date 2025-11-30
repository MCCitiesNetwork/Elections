package net.democracycraft.elections.api.service;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Service abstraction for exporting election data to a remote backend (e.g. GitHub Gists).
 * <p>
 * Implementations must be fully asynchronous and never block the Bukkit main thread.
 * Implementations are expected to be thread-safe.
 */
public interface GitHubGistService {

    /**
     * Publishes the given JSON payload as a new remote resource (e.g. a GitHub Gist).
     *
     * @param fileName    logical file name to use inside the remote container (e.g. "election.json")
     * @param jsonPayload election JSON payload to export
     * @return future that completes with the public URL of the created resource
     */
    CompletableFuture<String> publish(String fileName, String jsonPayload);

    /**
     * Publishes a gist that can contain both a JSON file and a human-readable Markdown file.
     * Any of the file names or contents may be {@code null} to skip that file.
     *
     * @param jsonFileName      optional JSON file name
     * @param jsonContent       optional JSON content
     * @param markdownFileName  optional Markdown file name
     * @param markdownContent   optional Markdown content
     * @return future with the HTML URL of the created gist
     */
    CompletableFuture<String> publish(@Nullable String jsonFileName,
                                      @Nullable String jsonContent,
                                      @Nullable String markdownFileName,
                                      @Nullable String markdownContent);
}
