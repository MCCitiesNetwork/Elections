package net.democracycraft.elections.command.subcommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.democracycraft.elections.api.model.Candidate;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.model.Vote;
import net.democracycraft.elections.api.model.Voter;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.data.*;
import net.democracycraft.elections.util.paste.PasteStorage;
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
 * - export <id>                -> export a single election (user-safe)
 * - export admin <id>          -> export with voter info embedded (manager-only)
 * - export delete <pasteId> confirm -> delete an existing paste (manager-only)
 */
public class ExportCommand implements Subcommand {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    @Override
    public List<String> names() { return List.of("export"); }

    @Override
    public String permission() { return "elections.user"; }

    @Override
    public String usage() { return "export <id> | export admin <id> | export delete <pasteId> [confirm]"; }

    @Override
    public void execute(CommandContext ctx) {
        if (ctx.args().length < 1) { ctx.usage(usage()); return; }
        String a0 = ctx.args()[0].toLowerCase(Locale.ROOT);
        switch (a0) {
            case "admin" -> executeAdminExport(ctx);
            case "delete" -> executeDelete(ctx);
            default -> executeUserExport(ctx);
        }
    }

    private void executeUserExport(CommandContext ctx) {
        int id = ctx.requireInt(0, "id");
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
                String json = buildJson(opt.get(), false, id2 -> null);
                PasteStorage ps = ctx.plugin().getPasteStorage();
                ps.putAsync(json).thenAccept(pasteId -> {
                    String url = ps.viewUrl(pasteId);
                    electionsService.markExportedAsync(opt.get().getId(), ctx.sender().getName());
                    ctx.sender().sendMessage("Exported to: " + url);
                    ctx.plugin().getLogger().info("[Export] actor=" + ctx.sender().getName() + ", electionId=" + opt.get().getId() + ", pasteId=" + pasteId);
                }).exceptionally(ex -> {
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                        ctx.plugin().getLogger().warning("[Export] actor=" + ctx.sender().getName() + ", electionId=" + opt.get().getId() + ", error=" + ex.getMessage());
                        ctx.sender().sendMessage("Export failed: " + ex.getMessage());
                    });
                    return null;
                });
            });
        });
    }

    private void executeAdminExport(CommandContext ctx) {
        if (!ctx.sender().hasPermission("elections.manager") && !ctx.sender().hasPermission("elections.admin") && !ctx.sender().hasPermission("democracyelections.admin")) {
            ctx.sender().sendMessage("You don't have permission.");
            return;
        }
        int id = ctx.requireInt(1, "id");
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
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> ctx.sender().sendMessage("Voters fetch failed: " + vErr.getMessage()));
                    return;
                }
                Map<Integer, String> map = voters.stream().collect(Collectors.toMap(Voter::getId, Voter::getName, (a,b) -> a));
                Function<Integer, String> provider = map::get;
                Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                    Election election = opt.get();
                    String json = buildJson(election, true, provider);
                    PasteStorage ps = ctx.plugin().getPasteStorage();
                    ps.putAsync(json).thenAccept(pasteId -> {
                        String url = ps.viewUrl(pasteId);
                        svc.markExportedAsync(id, ctx.sender().getName());
                        ctx.sender().sendMessage("Exported (admin) to: " + url);
                        ctx.plugin().getLogger().info("[ExportAdmin] actor=" + ctx.sender().getName() + ", electionId=" + id + ", pasteId=" + pasteId);
                    }).exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                            ctx.plugin().getLogger().warning("[ExportAdmin] actor=" + ctx.sender().getName() + ", electionId=" + id + ", error=" + ex.getMessage());
                            ctx.sender().sendMessage("Export failed: " + ex.getMessage());
                        });
                        return null;
                    });
                });
            });
        });
    }

    private void executeDelete(CommandContext ctx) {
        if (!ctx.sender().hasPermission("elections.manager") && !ctx.sender().hasPermission("elections.paste") && !ctx.sender().hasPermission("elections.admin") && !ctx.sender().hasPermission("democracyelections.admin")) {
            ctx.sender().sendMessage("You don't have permission.");
            return;
        }
        if (ctx.args().length < 2) { ctx.sender().sendMessage("Paste id is required"); return; }
        String id = ctx.args()[1];
        if (ctx.args().length < 3 || !"confirm".equalsIgnoreCase(ctx.args()[2])) {
            ctx.sender().sendMessage("This will delete paste '" + id + "'. Run again with 'confirm': /" + ctx.label() + " export delete " + id + " confirm");
            return;
        }
        PasteStorage ps = ctx.plugin().getPasteStorage();
        ps.deleteAsync(id, ok -> {
            ctx.plugin().getLogger().info("[ExportDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", result=" + ok);
            ctx.sender().sendMessage(ok ? "Paste deleted." : "Paste not deleted (unauthorized or not found).");
        }, ex -> {
            ctx.plugin().getLogger().warning("[ExportDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", error=" + ex.getMessage());
            ctx.sender().sendMessage("Delete failed: " + ex.getMessage());
        });
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        String[] a = ctx.args();
        if (a.length == 1) {
            List<String> base = ctx.filter(List.of("admin", "delete"), a[0]);
            List<String> ids = ctx.filter(ctx.electionIds(), a[0]);
            // merge without duplicates, prefer ids first for convenience
            return java.util.stream.Stream.concat(ids.stream(), base.stream()).distinct().toList();
        }
        if (a.length == 2) {
            if ("admin".equalsIgnoreCase(a[0])) return ctx.filter(ctx.electionIds(), a[1]);
            if ("delete".equalsIgnoreCase(a[0])) return List.of();
            // default export path expects id at a[0], but when completing second token we return empty
            return List.of();
        }
        if (a.length == 3 && "delete".equalsIgnoreCase(a[0])) return ctx.filter(List.of("confirm"), a[2]);
        return List.of();
    }

    /**
     * Builds a JSON snapshot for the given election.
     * @param election source election
     * @param includeVoterInBallots embed voter info into ballots when true
     * @param voterNameProvider provider to resolve voter names by id (may be null)
     * @return JSON string
     */
    private static String buildJson(Election election, boolean includeVoterInBallots, Function<Integer, String> voterNameProvider) {
        RequirementsDto requirements = election.getRequirements();
        ElectionDto electionDto = new ElectionDto(
                election.getId(),
                election.getTitle(),
                election.getSystem(),
                election.getMinimumVotes(),
                requirements,
                election.getCreatedAt()
        );
        electionDto.setStatus(election.getStatus());
        electionDto.setClosesAt(election.getClosesAt());
        electionDto.setDurationDays(election.getDurationDays());
        electionDto.setDurationTime(election.getDurationTime());

        for (Candidate c : election.getCandidates()) {
            electionDto.addCandidate(new CandidateDto(c.getId(), c.getName()));
        }
        election.getPolls().forEach(p -> electionDto.addPoll(new PollDto(p.getWorld(), p.getX(), p.getY(), p.getZ())));
        for (Vote b : election.getBallots()) {
            BallotDto bd = new BallotDto(b.getId(), b.getElectionId(), b.getVoterId());
            b.getSelections().forEach(bd::addSelection);
            bd.setSubmittedAt(b.getSubmittedAt());
            if (includeVoterInBallots && voterNameProvider != null) {
                String name = voterNameProvider.apply(b.getVoterId());
                if (name != null) bd.setVoter(new VoterDto(b.getVoterId(), name));
            }
            electionDto.appendBallot(bd);
        }
        election.getStatusChanges().forEach(electionDto::addStatusChange);
        return GSON.toJson(electionDto);
    }
}
