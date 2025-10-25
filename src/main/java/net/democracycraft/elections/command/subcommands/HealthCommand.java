package net.democracycraft.elections.command.subcommands;

import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;

import java.util.List;

import static net.democracycraft.elections.util.config.ConfigPaths.AUTO_CLOSE_SWEEP_SECONDS;
import static net.democracycraft.elections.util.config.ConfigPaths.PASTEGG_API_KEY;

/**
 * Shows basic health stats: counts, config warnings, and service hints.
 */
public class HealthCommand implements Subcommand {
    @Override
    public List<String> names() { return List.of("health"); }

    @Override
    public String permission() { return "elections.health"; }

    @Override
    public String usage() { return "health"; }

    @Override
    public void execute(CommandContext ctx) {
        var svc = ctx.electionsService();
        var list = svc.listElections();
        int total = list.size();
        int open = 0, closed = 0, deleted = 0, voters = 0, ballots = 0;
        for (Election e : list) {
            switch (e.getStatus()) {
                case OPEN -> open++;
                case CLOSED -> closed++;
                case DELETED -> deleted++;
            }
            voters += e.getVoterCount();
            ballots += e.getBallots().size();
        }
        int sweep = ctx.plugin().getConfig().getInt(AUTO_CLOSE_SWEEP_SECONDS.getPath(), 60);

        // Measure DB latency (SELECT 1)
        long dbMs;
        try {
            long t0 = System.nanoTime();
            ctx.plugin().getMySQLManager().withConnection(conn -> {
                try (var st = conn.createStatement()) { st.execute("SELECT 1"); }
                return null;
            });
            dbMs = (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception ex) {
            dbMs = -1L;
        }

        ctx.sender().sendMessage("Health: elections=" + total + " (open=" + open + ", closed=" + closed + ", deleted=" + deleted + "), voters=" + voters + ", ballots=" + ballots + ". AutoCloseSweepSeconds=" + sweep + ", dbLatencyMs=" + dbMs + ".");
        if (sweep > 300) ctx.sender().sendMessage("Warning: autoClose sweep interval is high (" + sweep + "s). Consider <= 120s.");
        String pasteKey = ctx.plugin().getConfig().getString(PASTEGG_API_KEY.getPath());
        if (pasteKey == null || pasteKey.isBlank()) ctx.sender().sendMessage("Warning: paste.gg API key not set. Delete operations may not be authorized.");
    }
}
