package net.democracycraft.democracyelections.command.subcommands;

import net.democracycraft.democracyelections.api.model.*;
import net.democracycraft.democracyelections.command.framework.CommandContext;
import net.democracycraft.democracyelections.command.framework.Subcommand;
import net.democracycraft.democracyelections.util.bytebin.BytebinClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ExportElection implements Subcommand {
    @Override
    public List<String> names() { return List.of("export"); }

    @Override
    public String permission() { return "elections.user"; }

    @Override
    public String usage() { return "export <id>"; }

    @Override
    public void execute(CommandContext ctx) {
        int id = ctx.requireInt(0, "id");
        Optional<Election> opt = ctx.svc.getElection(id);
        if (opt.isEmpty()) { ctx.sender.sendMessage("Election not found."); return; }
        String json = buildJson(opt.get(), false, ctx);
        try {
            String url = BytebinClient.uploadJson(json);
            ctx.sender.sendMessage("Exported to: " + url);
        } catch (Exception ex) {
            ctx.sender.sendMessage("Export failed: " + ex.getMessage());
        }
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args.length==1) return ctx.filter(ctx.electionIds(), ctx.args[0]);
        return List.of();
    }

    protected static String buildJson(Election e, boolean includeMapping, CommandContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "id").append(e.getId()).append(',');
        kvs(sb, "title", e.getTitle()).append(',');
        kvs(sb, "status", e.getStatus().name()).append(',');
        kvs(sb, "system", e.getSystem().name()).append(',');
        kv(sb, "minimumVotes").append(e.getMinimumVotes()).append(',');
        if (e.getCreatedAt()!=null) { kvs(sb, "createdAt", CommandContext.tsToString(e.getCreatedAt())).append(','); }
        if (e.getClosesAt()!=null) { kvs(sb, "closesAt", CommandContext.tsToString(e.getClosesAt())).append(','); }
        // candidates
        sb.append("\"candidates\":");
        sb.append('[');
        List<Candidate> cs = e.getCandidates();
        for (int i=0;i<cs.size();i++) {
            Candidate c = cs.get(i);
            sb.append('{');
            kv(sb, "id").append(c.getId()).append(',');
            kvs(sb, "name", c.getName());
            sb.append('}'); if (i<cs.size()-1) sb.append(',');
        }
        sb.append(']').append(',');
        // polls
        sb.append("\"polls\":");
        sb.append('[');
        List<Poll> ps = e.getPolls();
        for (int i=0;i<ps.size();i++) {
            Poll p = ps.get(i);
            sb.append('{');
            kvs(sb, "world", p.getWorld()).append(',');
            kv(sb, "x").append(p.getX()).append(',');
            kv(sb, "y").append(p.getY()).append(',');
            kv(sb, "z").append(p.getZ());
            sb.append('}'); if (i<ps.size()-1) sb.append(',');
        }
        sb.append(']').append(',');
        // ballots
        sb.append("\"ballots\":");
        sb.append('[');
        List<Vote> bs = e.getBallots();
        for (int i=0;i<bs.size();i++) {
            Vote b = bs.get(i);
            sb.append('{');
            kv(sb, "voterId").append(b.getVoterId()).append(',');
            sb.append("\"selections\":");
            sb.append('[');
            List<Integer> picks = b.getSelections();
            for (int j=0;j<picks.size();j++) { sb.append(picks.get(j)); if (j<picks.size()-1) sb.append(','); }
            sb.append(']').append(',');
            if (b.getSubmittedAt()!=null) kvs(sb, "submittedAt", CommandContext.tsToString(b.getSubmittedAt()));
            sb.append('}'); if (i<bs.size()-1) sb.append(',');
        }
        sb.append(']').append(',');
        // status changes
        sb.append("\"statusChanges\":");
        sb.append('[');
        var changes = e.getStatusChanges();
        for (int i=0;i<changes.size();i++) {
            var ch = changes.get(i);
            sb.append('{');
            kvs(sb, "time", CommandContext.tsToString(ch.getAt())).append(',');
            kvs(sb, "type", ch.getType().name());
            sb.append('}'); if (i<changes.size()-1) sb.append(',');
        }
        sb.append(']');
        // optional voter mapping (admin only)
        if (includeMapping) {
            sb.append(',');
            sb.append("\"voters\":");
            sb.append('[');
            List<Voter> vs = ctx.svc.listVoters(e.getId());
            for (int i=0;i<vs.size();i++) {
                Voter v = vs.get(i);
                sb.append('{');
                kv(sb, "id").append(v.getId()).append(',');
                kvs(sb, "name", v.getName());
                sb.append('}'); if (i<vs.size()-1) sb.append(',');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    protected static StringBuilder kv(StringBuilder sb, String key) {
        sb.append('"').append(escape(key)).append('"').append(':');
        return sb;
    }

    protected static StringBuilder kvs(StringBuilder sb, String key, String value) {
        kv(sb, key).append('"').append(escape(value)).append('"');
        return sb;
    }

    protected static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else out.append(c);
                }
            }
        }
        return out.toString();
    }
}

