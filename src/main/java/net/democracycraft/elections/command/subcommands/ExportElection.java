package net.democracycraft.elections.command.subcommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.democracycraft.elections.api.model.*;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.data.*;
import net.democracycraft.elections.util.paste.PasteStorage;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.function.Function;

/**
 * Subcommand to export a single election to paste.gg.
 *
 * Threading:
 * - Election lookup is async via service API.
 * - JSON serialization runs on a Bukkit async task.
 * - Paste upload uses PasteStorage async facade; callbacks complete on main thread.
 * - Database markExported uses async API.
 */
public class ExportElection implements Subcommand {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    @Override
    public List<String> names() { return List.of("export"); }

    @Override
    public String permission() { return "elections.user"; }

    @Override
    public String usage() { return "export <id>"; }

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
            // Build JSON off the main thread
            Bukkit.getScheduler().runTaskAsynchronously(ctx.plugin(), () -> {
                String json = buildJson(opt.get(), false, ctx, voterId -> null);
                PasteStorage pasteStorage = ctx.plugin().getPasteStorage();
                pasteStorage.putAsync(json).thenAccept(pasteId -> {
                    String url = pasteStorage.viewUrl(pasteId);
                    // mark exported asynchronously
                    svc.markExportedAsync(id, ctx.sender().getName());
                    ctx.sender().sendMessage("Exported to: " + url);
                    ctx.plugin().getLogger().info("[Export] actor=" + ctx.sender().getName() + ", electionId=" + id + ", pasteId=" + pasteId);
                }).exceptionally(ex -> {
                    // Complete on main thread due to facade, but ensure safety
                    Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
                        ctx.plugin().getLogger().warning("[Export] actor=" + ctx.sender().getName() + ", electionId=" + id + ", error=" + ex.getMessage());
                        ctx.sender().sendMessage("Export failed: " + ex.getMessage());
                    });
                    return null;
                });
            });
        });
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args().length==1) return ctx.filter(ctx.electionIds(), ctx.args()[0]);
        return List.of();
    }

    protected static String buildJson(Election election, boolean includeVoterInBallots, CommandContext ctx, Function<Integer, String> voterNameProvider) {
        // Build a fresh ElectionDto using current model values
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

        // candidates
        for (Candidate c : election.getCandidates()) {
            electionDto.addCandidate(new CandidateDto(c.getId(), c.getName()));
        }
        // polls
        for (Poll p : election.getPolls()) {
            electionDto.addPoll(new PollDto(p.getWorld(), p.getX(), p.getY(), p.getZ()));
        }
        // ballots
        for (Vote b : election.getBallots()) {
            BallotDto bd = new BallotDto(b.getId(), b.getElectionId(), b.getVoterId());
            // selections
            for (Integer sel : b.getSelections()) bd.addSelection(sel);
            // submitted at
            bd.setSubmittedAt(b.getSubmittedAt());
            // voter embedding for admin export using provided lookup
            if (includeVoterInBallots && voterNameProvider != null) {
                String name = voterNameProvider.apply(b.getVoterId());
                if (name != null) bd.setVoter(new VoterDto(b.getVoterId(), name));
            }
            electionDto.appendBallot(bd);
        }
        // status changes
        for (StatusChangeDto sc : election.getStatusChanges()) electionDto.addStatusChange(sc);

        // Gson will ignore voters map by design (transient) and only serialize embedded voter if present in ballots
        return GSON.toJson(electionDto);
    }
}
