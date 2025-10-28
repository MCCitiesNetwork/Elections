package net.democracycraft.elections.command.subcommands;

import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.model.Vote;
import net.democracycraft.elections.api.model.Voter;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.data.*;
import net.democracycraft.elections.util.export.PasteStorage;
import net.democracycraft.elections.util.export.local.queue.LocalExportedElectionQueue;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Export-related operations grouped under a single subcommand.
 *
 * Supported usages:
 * - export <id>                          -> publish remotely (user-safe)
 * - export local <id>                    -> save locally to the queue
 * - export both <id>                     -> publish remotely and also save locally
 * - export admin <id>                    -> like export <id> but with voter names
 * - export admin local <id>              -> local-only with voter names
 * - export admin both <id>               -> remote + local with voter names
 * - export delete <id> confirm           -> delete a remote publication (managers)
 * - export dispatch                      -> process the entire local queue (managers)
 * - export ballots <local|online> <id>   -> export only ballots (anonymous), to local file or online
 */
public class ExportCommand implements Subcommand {

    private enum Mode { REMOTE, LOCAL, BOTH }

    @Override
    public List<String> names() { return List.of("export"); }

    @Override
    public String permission() { return "elections.export"; }

    @Override
    public String usage() { return "export <id> | export local <id> | export both <id> | export admin [local|both] <id> | export delete <id> [confirm] | export dispatch | export ballots <local|online> <id>"; }

    @Override
    public void execute(CommandContext ctx) {
        if (ctx.args().length < 1) { ctx.usage(usage()); return; }
        String a0 = ctx.args()[0].toLowerCase(Locale.ROOT);
        switch (a0) {
            case "admin" -> executeAdminExport(ctx);
            case "delete" -> executeDelete(ctx);
            case "dispatch" -> executeDispatch(ctx);
            case "local" -> executeUserExport(ctx, Mode.LOCAL);
            case "both" -> executeUserExport(ctx, Mode.BOTH);
            case "ballots" -> executeBallotsExport(ctx);
            default -> executeUserExport(ctx, Mode.REMOTE);
        }
    }

    private void executeUserExport(CommandContext ctx, Mode mode) {
        int idx = (mode == Mode.REMOTE ? 0 : 1);
        int id = ctx.requireInt(idx, "id");
        ElectionsService electionsService = ctx.electionsService();
        electionsService.getElectionAsync(id).whenComplete((opt, err) -> {
            if (err != null) {
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Lookup failed: " + err.getMessage()));
                return;
            }
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Election not found."));
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                Election election = opt.get();
                String json = election.toJson(false, id2 -> null);
                LocalExportedElectionQueue queue = ctx.plugin().getLocalExportQueue();
                PasteStorage storage = ctx.plugin().getPasteStorage();

                switch (mode) {
                    case LOCAL -> {
                        queue.enqueue(election.getId(), json).thenAccept(f -> {
                            ctx.sender().sendMessage("Saved to local queue: " + f.getName());
                            ctx.plugin().getLogger().info("[ExportLocal] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", file=" + f.getName());
                        }).exceptionally(ex -> {
                            Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Could not save locally: " + ex.getMessage()));
                            return null;
                        });
                    }
                    case BOTH -> {
                        storage.putAsync(json).thenAccept(pasteId -> {
                            String url = storage.viewUrl(pasteId);
                            electionsService.markExportedAsync(election.getId(), ctx.sender().getName());
                            ctx.sender().sendMessage("Published: " + url);
                            ctx.plugin().getLogger().info("[ExportBoth] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", pasteId=" + pasteId);
                            // also keep a local copy
                            queue.enqueue(election.getId(), json);
                        }).exceptionally(ex -> {
                            // Fallback: save locally
                            queue.enqueue(election.getId(), json).thenAccept(f -> {
                                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Remote publish failed, saved to local queue: " + f.getName()));
                                ctx.plugin().getLogger().warning("[ExportBoth->LocalFallback] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", error=" + ex.getMessage());
                            });
                            return null;
                        });
                    }
                    case REMOTE -> {
                        storage.putAsync(json).thenAccept(pasteId -> {
                            String url = storage.viewUrl(pasteId);
                            electionsService.markExportedAsync(election.getId(), ctx.sender().getName());
                            ctx.sender().sendMessage("Published: " + url);
                            ctx.plugin().getLogger().info("[Export] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", pasteId=" + pasteId);
                        }).exceptionally(ex -> {
                            // Fallback: save locally
                            queue.enqueue(election.getId(), json).thenAccept(f -> {
                                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Remote publish failed, saved to local queue: " + f.getName()));
                                ctx.plugin().getLogger().warning("[Export->LocalFallback] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", error=" + ex.getMessage());
                            });
                            return null;
                        });
                    }
                }
            });
        });
    }

    private void executeAdminExport(CommandContext ctx) {
        if (!ctx.sender().hasPermission("elections.manager") && !ctx.sender().hasPermission("elections.admin")) {
            ctx.sender().sendMessage("You don't have permission.");
            return;
        }
        // Syntax: admin [local|both] <id>
        String[] a = ctx.args();
        Mode mode;
        int idIdx;
        if (a.length >= 3 && ("local".equalsIgnoreCase(a[1]) || "both".equalsIgnoreCase(a[1]))) {
            mode = "local".equalsIgnoreCase(a[1]) ? Mode.LOCAL : Mode.BOTH;
            idIdx = 2;
        } else {
            mode = Mode.REMOTE;
            idIdx = 1;
        }
        int id = ctx.requireInt(idIdx, "id");
        ElectionsService svc = ctx.electionsService();
        svc.getElectionAsync(id).whenComplete((opt, err) -> {
            if (err != null) {
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Lookup failed: " + err.getMessage()));
                return;
            }
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Election not found."));
                return;
            }
            svc.listVotersAsync(id).whenComplete((voters, vErr) -> {
                if (vErr != null) {
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Failed to fetch voters: " + vErr.getMessage()));
                    return;
                }
                Map<Integer, String> map = voters.stream().collect(Collectors.toMap(Voter::getId, Voter::getName, (a1,b) -> a1));
                Function<Integer, String> provider = map::get;
                Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                    Election election = opt.get();
                    String json = election.toJson(true, provider);
                    LocalExportedElectionQueue queue = ctx.plugin().getLocalExportQueue();
                    PasteStorage ps = ctx.plugin().getPasteStorage();

                    switch (mode) {
                        case LOCAL -> {
                            queue.enqueue(id, json).thenAccept(f -> {
                                ctx.sender().sendMessage("Saved to local queue: " + f.getName());
                                ctx.plugin().getLogger().info("[ExportAdminLocal] actor=" + ctx.sender().getName() + ", electionId=" + id + ", file=" + f.getName());
                            }).exceptionally(ex -> {
                                Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Could not save locally: " + ex.getMessage()));
                                return null;
                            });
                        }
                        case BOTH -> {
                            ps.putAsync(json).thenAccept(pasteId -> {
                                String url = ps.viewUrl(pasteId);
                                svc.markExportedAsync(id, ctx.sender().getName());
                                ctx.sender().sendMessage("Published (admin): " + url);
                                ctx.plugin().getLogger().info("[ExportAdminBoth] actor=" + ctx.sender().getName() + ", electionId=" + id + ", pasteId=" + pasteId);
                                // also keep a local copy
                                queue.enqueue(id, json);
                            }).exceptionally(ex -> {
                                // Fallback: save locally
                                queue.enqueue(id, json).thenAccept(f -> {
                                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Remote publish failed, saved to local queue: " + f.getName()));
                                    ctx.plugin().getLogger().warning("[ExportAdminBoth->LocalFallback] actor=" + ctx.sender().getName() + ", electionId=" + id + ", error=" + ex.getMessage());
                                });
                                return null;
                            });
                        }
                        case REMOTE -> {
                            ps.putAsync(json).thenAccept(pasteId -> {
                                String url = ps.viewUrl(pasteId);
                                svc.markExportedAsync(id, ctx.sender().getName());
                                ctx.sender().sendMessage("Published (admin): " + url);
                                ctx.plugin().getLogger().info("[ExportAdmin] actor=" + ctx.sender().getName() + ", electionId=" + id + ", pasteId=" + pasteId);
                            }).exceptionally(ex -> {
                                // Fallback: save locally
                                queue.enqueue(id, json).thenAccept(f -> {
                                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Remote publish failed, saved to local queue: " + f.getName()));
                                    ctx.plugin().getLogger().warning("[ExportAdmin->LocalFallback] actor=" + ctx.sender().getName() + ", electionId=" + id + ", error=" + ex.getMessage());
                                });
                                return null;
                            });
                        }
                    }
                });
            });
        });
    }

    private void executeDelete(CommandContext ctx) {
        if (!ctx.sender().hasPermission("elections.manager") && !ctx.sender().hasPermission("elections.paste") && !ctx.sender().hasPermission("elections.admin")) {
            ctx.sender().sendMessage("You don't have permission.");
            return;
        }
        if (ctx.args().length < 2) { ctx.sender().sendMessage("ID required"); return; }
        String id = ctx.args()[1];
        if (ctx.args().length < 3 || !"confirm".equalsIgnoreCase(ctx.args()[2])) {
            ctx.sender().sendMessage("This will delete publication '" + id + "'. Run again with 'confirm': /" + ctx.label() + " export delete " + id + " confirm");
            return;
        }
        PasteStorage ps = ctx.plugin().getPasteStorage();
        ps.deleteAsync(id, ok -> {
            ctx.plugin().getLogger().info("[ExportDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", result=" + ok);
            ctx.sender().sendMessage(ok ? "Deleted." : "Not deleted (unauthorized or not found).");
        }, ex -> {
            ctx.plugin().getLogger().warning("[ExportDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", error=" + ex.getMessage());
            ctx.sender().sendMessage("Delete failed: " + ex.getMessage());
        });
    }

    private void executeDispatch(CommandContext ctx) {
        if (!ctx.sender().hasPermission("elections.manager") && !ctx.sender().hasPermission("elections.admin")) {
            ctx.sender().sendMessage("You don't have permission.");
            return;
        }
        LocalExportedElectionQueue queue = ctx.plugin().getLocalExportQueue();
        PasteStorage storage = ctx.plugin().getPasteStorage();
        ElectionsService svc = ctx.electionsService();
        queue.processAll(storage, svc, ctx.sender().getName()).thenAccept(report -> {
            ctx.sender().sendMessage("Queue processed: total=" + report.total() + ", published=" + report.uploaded() + ", skipped=" + report.skipped() + ", failed=" + report.failed());
            ctx.plugin().getLogger().info("[ExportDispatch] actor=" + ctx.sender().getName() + ", total=" + report.total() + ", uploaded=" + report.uploaded() + ", skipped=" + report.skipped() + ", failed=" + report.failed());
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Queue processing failed: " + ex.getMessage()));
            return null;
        });
    }

    /**
     * Publish only the ballots of a given election as JSON.
     * Usage: /elections export ballots <local|online> <electionId>
     *
     * Behavior: anonymous export (no voter names). Local writes to exports/ballots, Online publishes to paste service.
     */
    private void executeBallotsExport(CommandContext ctx) {
        if (ctx.args().length < 3) {
            ctx.sender().sendMessage("Usage: /" + ctx.label() + " export ballots <local|online> <electionId>");
            return;
        }
        String mode = ctx.args()[1].toLowerCase(java.util.Locale.ROOT);
        boolean isLocal = "local".equals(mode);
        boolean isOnline = "online".equals(mode);
        if (!isLocal && !isOnline) {
            ctx.sender().sendMessage("Invalid mode. Use 'local' or 'online'.");
            return;
        }
        int id = ctx.requireInt(2, "id");
        ElectionsService electionsService = ctx.electionsService();
        electionsService.getElectionAsync(id).whenComplete((opt, err) -> {
            if (err != null) {
                org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Lookup failed: " + err.getMessage()));
                return;
            }
            if (opt.isEmpty()) {
                org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Election not found."));
                return;
            }
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                net.democracycraft.elections.api.model.Election election = opt.get();
                // Build a lightweight JSON array of ballots (anonymous, no voter names)
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();
                java.util.List<net.democracycraft.elections.data.BallotDto> ballots = election.getBallots().stream().map(v -> {
                    net.democracycraft.elections.data.BallotDto bd = new net.democracycraft.elections.data.BallotDto(v.getId(), election.getId(), v.getVoterId());
                    for (Integer sel : v.getSelections()) bd.addSelection(sel);
                    bd.setSubmittedAt(v.getSubmittedAt());
                    return bd;
                }).toList();
                String json = gson.toJson(ballots);

                if (isLocal) {
                    // Write to exports/ballots directory
                    java.io.File base = new java.io.File(ctx.plugin().getDataFolder(), net.democracycraft.elections.util.config.DataFolder.EXPORTS.getPath());
                    java.io.File ballotsDir = new java.io.File(base, "ballots");
                    if (!ballotsDir.exists() && !ballotsDir.mkdirs() && !ballotsDir.exists()) {
                        org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Could not create ballots directory."));
                        return;
                    }
                    String ts = String.valueOf(System.currentTimeMillis());
                    java.io.File out = new java.io.File(ballotsDir, "ballots-" + election.getId() + "-" + ts + ".json");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                        fos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Saved ballots to: " + out.getName()));
                        ctx.plugin().getLogger().info("[ExportBallotsLocal] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", file=" + out.getName());
                    } catch (java.io.IOException io) {
                        org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Ballots local save failed: " + io.getMessage()));
                        ctx.plugin().getLogger().warning("[ExportBallotsLocal] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", error=" + io.getMessage());
                    }
                } else {
                    // Online publish
                    net.democracycraft.elections.util.export.PasteStorage storage = ctx.plugin().getPasteStorage();
                    storage.putAsync(json).thenAccept(pasteId -> {
                        String url = storage.viewUrl(pasteId);
                        ctx.sender().sendMessage("Published ballots: " + url);
                        ctx.plugin().getLogger().info("[ExportBallotsOnline] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", pasteId=" + pasteId + ", count=" + ballots.size());
                    }).exceptionally(ex -> {
                        org.bukkit.Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Ballots publish failed: " + ex.getMessage()));
                        ctx.plugin().getLogger().warning("[ExportBallotsOnline] actor=" + ctx.sender().getName() + ", electionId=" + election.getId() + ", error=" + ex.getMessage());
                        return null;
                    });
                }
            });
        });
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        String[] a = ctx.args();
        if (a.length == 1) {
            List<String> base = ctx.filter(List.of("admin", "delete", "dispatch", "local", "both", "ballots"), a[0]);
            List<String> ids = ctx.filter(ctx.electionIds(), a[0]);
            // merge without duplicates, prefer ids first for convenience
            return java.util.stream.Stream.concat(ids.stream(), base.stream()).distinct().toList();
        }
        if (a.length == 2) {
            if ("admin".equalsIgnoreCase(a[0])) return ctx.filter(List.of("local", "both"), a[1]);
            if ("delete".equalsIgnoreCase(a[0]) || "dispatch".equalsIgnoreCase(a[0])) return List.of();
            if ("ballots".equalsIgnoreCase(a[0])) return ctx.filter(List.of("local", "online"), a[1]);
            if ("local".equalsIgnoreCase(a[0]) || "both".equalsIgnoreCase(a[0])) return ctx.filter(ctx.electionIds(), a[1]);
            // default export path expects id at a[0], but when completing second token we return empty
            return List.of();
        }
        if (a.length == 3) {
            if ("admin".equalsIgnoreCase(a[0])) return ctx.filter(ctx.electionIds(), a[2]);
            if ("delete".equalsIgnoreCase(a[0])) return ctx.filter(List.of("confirm"), a[2]);
            if ("ballots".equalsIgnoreCase(a[0])) return ctx.filter(ctx.electionIds(), a[2]);
        }
        return List.of();
    }

}
