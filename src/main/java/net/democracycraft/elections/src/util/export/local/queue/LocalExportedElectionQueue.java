package net.democracycraft.elections.src.util.export.local.queue;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.src.data.StateChangeType;
import net.democracycraft.elections.src.util.config.DataFolder;
import net.democracycraft.elections.api.service.GitHubGistService;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local export queue: stores election JSON files on disk to be processed later.
 *
 * Directories:
 * - exports/queue: pending files (election-<id>-<epoch>.json)
 * - exports/sent: archived after successful publish
 * - exports/failed: files that could not be processed (moved here)
 */
public class LocalExportedElectionQueue {

    private final Elections plugin;
    private final File queueDir;
    private final File sentDir;
    private final File failedDir;

    private static final Pattern FILENAME = Pattern.compile("^election-([0-9]+)-([0-9]+)\\.json$");

    public LocalExportedElectionQueue(Elections plugin) {
        this.plugin = plugin;
        File base = new File(plugin.getDataFolder(), DataFolder.EXPORTS.getPath());
        this.queueDir = new File(base, "queue");
        this.sentDir = new File(base, "sent");
        this.failedDir = new File(base, "failed");
        ensureDir(queueDir);
        ensureDir(sentDir);
        ensureDir(failedDir);
    }

    public File getQueueDir() { return queueDir; }

    private void ensureDir(File d) {
        if (!d.exists() && !d.mkdirs() && !d.exists()) {
            plugin.getLogger().warning("Could not create directory: " + d.getAbsolutePath());
        }
    }

    /**
     * Enqueues the JSON of an election for later export.
     * Runs off the main thread.
     */
    public CompletableFuture<File> enqueue(int electionId, String json) {
        CompletableFuture<File> cf = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override public void run() {
                try {
                    String ts = String.valueOf(System.currentTimeMillis());
                    File out = new File(queueDir, "election-" + electionId + "-" + ts + ".json");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] bytes = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
                        fos.write(bytes);
                    }
                    new BukkitRunnable() { @Override public void run() { cf.complete(out); } }.runTask(plugin);
                } catch (IOException ex) {
                    new BukkitRunnable() { @Override public void run() { cf.completeExceptionally(ex); } }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
        return cf;
    }

    /**
     * Processes all files in the queue, publishing them one by one using the provided GitHubGistService.
     * Checks beforehand whether the election was already exported; if so, the file is removed.
     * Returns a report with totals.
     */
    public CompletableFuture<Report> processAll(GitHubGistService gistService, ElectionsService service, String actor) {
        CompletableFuture<Report> cf = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override public void run() {
                List<File> files = listQueueFiles();
                AtomicInteger uploaded = new AtomicInteger();
                AtomicInteger skipped = new AtomicInteger();
                AtomicInteger failed = new AtomicInteger();

                processNext(0, files, gistService, service, actor, uploaded, skipped, failed, () -> {
                    Report r = new Report(files.size(), uploaded.get(), skipped.get(), failed.get());
                    new BukkitRunnable() { @Override public void run() { cf.complete(r); } }.runTask(plugin);
                });
            }
        }.runTaskAsynchronously(plugin);
        return cf;
    }

    private void processNext(int i, List<File> files, GitHubGistService gistService, ElectionsService service, String actor,
                              AtomicInteger uploaded, AtomicInteger skipped, AtomicInteger failed, Runnable done) {
        if (i >= files.size()) { done.run(); return; }
        File f = files.get(i);
        int electionId = parseElectionId(f.getName());
        if (electionId <= 0) {
            // invalid filename: move to failed
            moveTo(f, new File(failedDir, f.getName()));
            failed.incrementAndGet();
            processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
            return;
        }
        try {
            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            // check current state before publishing
            service.getElectionAsync(electionId).whenComplete((opt, err) -> {
                if (err != null || opt.isEmpty()) {
                    // DB error or election not found: keep in queue and count as failure
                    failed.incrementAndGet();
                    processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
                    return;
                }
                Election e = opt.get();
                boolean already = e.getStatusChanges().stream().anyMatch(sc -> sc.type() == StateChangeType.EXPORTED);
                if (already) {
                    // already exported: remove from queue
                    safeDelete(f);
                    skipped.incrementAndGet();
                    processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
                    return;
                }
                // publish to GitHub Gist
                String gistFileName = "election-" + electionId + ".json";
                gistService.publish(gistFileName, content).whenComplete((url, ex) -> {
                    if (ex != null) {
                        plugin.getLogger().warning("Queue publish failed for electionId=" + electionId + ": " + ex.getMessage());
                        // keep in queue and count as failure
                        failed.incrementAndGet();
                        processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
                        return;
                    }
                    // mark exported and archive locally
                    service.markExportedAsync(electionId, actor);
                    String stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                    String safeStamp = stamp.replace(':', '-');
                    File dest = new File(sentDir, f.getName().replace(".json", "-" + safeStamp + ".json"));
                    moveTo(f, dest);
                    uploaded.incrementAndGet();
                    processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
                });
            });
        } catch (IOException io) {
            moveTo(f, new File(failedDir, f.getName()));
            failed.incrementAndGet();
            processNext(i+1, files, gistService, service, actor, uploaded, skipped, failed, done);
        }
    }

    private List<File> listQueueFiles() {
        File[] arr = queueDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (arr == null || arr.length == 0) return new ArrayList<>();
        Arrays.sort(arr, (a,b) -> a.getName().compareToIgnoreCase(b.getName()));
        return Arrays.asList(arr);
    }

    private int parseElectionId(String name) {
        Matcher m = FILENAME.matcher(name);
        if (!m.matches()) return -1;
        try { return Integer.parseInt(m.group(1)); }
        catch (NumberFormatException nfe) { return -1; }
    }

    private void safeDelete(File f) {
        try { Files.deleteIfExists(f.toPath()); }
        catch (Exception ignored) {}
    }

    private void moveTo(File src, File dest) {
        try {
            Files.createDirectories(dest.getParentFile().toPath());
            Files.move(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not move queue file: " + src.getName() + " -> " + dest.getName() + ": " + e.getMessage());
        }
    }

    /** Processing summary. */
    public record Report(int total, int uploaded, int skipped, int failed) {}
}
