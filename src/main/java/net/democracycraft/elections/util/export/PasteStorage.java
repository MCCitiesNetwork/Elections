package net.democracycraft.elections.util.export;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstraction for a paste storage backend.
 *
 * Contract (asynchronous-only):
 * - All network I/O must run off the main server thread.
 * - Success and error callbacks are invoked on the Bukkit main thread unless otherwise documented.
 * - Implementations should provide sensible timeouts and error propagation.
 */
public interface PasteStorage {

    /**
     * Uploads content to the paste backend asynchronously.
     *
     * Inputs:
     * - content: raw string to upload (implementation escapes/encodes as needed).
     *
     * Outputs:
     * - onSuccess: called with the paste id upon successful upload (main thread).
     * - onError: called with the failure cause (main thread).
     */
    void putAsync(String content, Consumer<String> onSuccess, Consumer<Throwable> onError);

    /**
     * Retrieves a paste by id asynchronously.
     *
     * Outputs:
     * - onSuccess: called with the response body or null if not found (main thread).
     * - onError: called with the failure cause (main thread).
     */
    void getAsync(String id, Consumer<String> onSuccess, Consumer<Throwable> onError);

    /**
     * Deletes a paste by id asynchronously.
     *
     * Outputs:
     * - onSuccess: called with true if deleted or not found; false if unauthorized (main thread).
     * - onError: called with the failure cause (main thread).
     */
    void deleteAsync(String id, Consumer<Boolean> onSuccess, Consumer<Throwable> onError);

    /**
     * Builds a view URL (human-friendly) for a paste id.
     *
     * Pure function, no I/O.
     */
    String viewUrl(String id);

    // --- CompletableFuture facade (developer-friendly) ---

    /**
     * CompletableFuture-based facade for putAsync. Completion and failure semantics are identical
     * to the callback variant. The future is completed on the Bukkit main thread.
     */
    default CompletableFuture<String> putAsync(String content) {
        CompletableFuture<String> cf = new CompletableFuture<>();
        putAsync(content, cf::complete, cf::completeExceptionally);
        return cf;
    }

    /** Future facade for getAsync (may complete with null when paste is not found). */
    default CompletableFuture<String> getAsync(String id) {
        CompletableFuture<String> cf = new CompletableFuture<>();
        getAsync(id, cf::complete, cf::completeExceptionally);
        return cf;
    }

    /** Future facade for deleteAsync. */
    default CompletableFuture<Boolean> deleteAsync(String id) {
        CompletableFuture<Boolean> cf = new CompletableFuture<>();
        deleteAsync(id, cf::complete, cf::completeExceptionally);
        return cf;
    }
}
