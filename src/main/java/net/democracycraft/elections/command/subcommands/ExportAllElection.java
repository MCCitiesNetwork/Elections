package net.democracycraft.elections.command.subcommands;

import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.model.Voter;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.util.paste.PasteStorage;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Subcommand to export an election with full admin details (includes voters in ballots) to paste.gg.
 *
 * Threading:
 * - Election lookup is async via service API.
 * - JSON serialization runs on a Bukkit async task.
 * - Paste upload uses PasteStorage async facade; callbacks complete on main thread.
 * - Database markExported uses async API.
 */
public class ExportAllElection implements Subcommand {
    @Override
    public List<String> names() { return List.of("exportall"); }

    @Override
    public String permission() { return "elections.manager"; }

    @Override
    public String usage() { return "exportall <id>"; }

    @Override
    public void execute(CommandContext ctx) {
        int id = ctx.requireInt(0, "id");
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
            // Fetch voters asynchronously, then build JSON on an async thread
            svc.listVotersAsync(id).whenComplete((voters, vErr) -> {
                if (vErr != null) {
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Voters fetch failed: " + vErr.getMessage()));
                    return;
                }
                Map<Integer, String> idToName = voters.stream().collect(Collectors.toMap(Voter::getId, Voter::getName, (a,b) -> a));
                Function<Integer, String> provider = idToName::get;
                Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                    Election election = opt.get();
                    String json = ExportElection.buildJson(election, true, ctx, provider);
                    PasteStorage pasteStorage = ctx.plugin().getPasteStorage();
                    pasteStorage.putAsync(json).thenAccept(pasteId -> {
                        String url = pasteStorage.viewUrl(pasteId);
                        svc.markExportedAsync(id, ctx.sender().getName());
                        ctx.sender().sendMessage("Exported (admin) to: " + url);
                        ctx.plugin().getLogger().info("[ExportAll] actor=" + ctx.sender().getName() + ", electionId=" + id + ", pasteId=" + pasteId);
                    }).exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                            ctx.plugin().getLogger().warning("[ExportAll] actor=" + ctx.sender().getName() + ", electionId=" + id + ", error=" + ex.getMessage());
                            ctx.sender().sendMessage("Export failed: " + ex.getMessage());
                        });
                        return null;
                    });
                });
            });
        });
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args().length==1) return ctx.filter(ctx.electionIds(), ctx.args()[0]);
        return List.of();
    }
}
