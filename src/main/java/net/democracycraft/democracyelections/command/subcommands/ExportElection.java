package net.democracycraft.democracyelections.command.subcommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.democracycraft.democracyelections.api.model.*;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.command.framework.CommandContext;
import net.democracycraft.democracyelections.command.framework.Subcommand;
import net.democracycraft.democracyelections.data.*;
import net.democracycraft.democracyelections.util.bytebin.ExportClient;

import java.util.List;
import java.util.Optional;

public class ExportElection implements Subcommand {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Override
    public List<String> names() { return List.of("export"); }

    @Override
    public String permission() { return "elections.user"; }

    @Override
    public String usage() { return "export <id>"; }

    @Override
    public void execute(CommandContext ctx) {
        int id = ctx.requireInt(0, "id");
        Optional<Election> opt = ctx.electionsService().getElection(id);
        if (opt.isEmpty()) { ctx.sender().sendMessage("Election not found."); return; }
        String json = buildJson(opt.get(), false, ctx);
        try {
            String url = ExportClient.uploadJson(json);
            ctx.sender().sendMessage("Exported to: " + url);
            ctx.electionsService().markExported(id);
        } catch (Exception ex) {
            ctx.sender().sendMessage("Export failed: " + ex.getMessage());
        }
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args().length==1) return ctx.filter(ctx.electionIds(), ctx.args()[0]);
        return List.of();
    }

    protected static String buildJson(Election election, boolean includeVoterInBallots, CommandContext ctx) {
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
        ElectionsService svc = ctx.electionsService();
        for (Vote b : election.getBallots()) {
            BallotDto bd = new BallotDto(b.getId(), b.getElectionId(), b.getVoterId());
            // selections
            for (Integer sel : b.getSelections()) bd.addSelection(sel);
            // submitted at
            bd.setSubmittedAt(b.getSubmittedAt());
            // voter embedding for admin export
            if (includeVoterInBallots) {
                svc.getVoterById(election.getId(), b.getVoterId()).ifPresent(v -> bd.setVoter(new VoterDto(v.getId(), v.getName())));
            }
            electionDto.appendBallot(bd);
        }
        // status changes
        for (StatusChangeDto sc : election.getStatusChanges()) electionDto.addStatusChange(sc);

        // Gson will ignore voters map by design (transient) and only serialize embedded voter if present in ballots
        return GSON.toJson(electionDto);
    }
}
