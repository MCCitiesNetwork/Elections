package net.democracycraft.democracyelections.command.subcommands;

import net.democracycraft.democracyelections.api.model.Election;
import net.democracycraft.democracyelections.command.framework.CommandContext;
import net.democracycraft.democracyelections.command.framework.Subcommand;
import net.democracycraft.democracyelections.util.bytebin.BytebinClient;

import java.util.List;
import java.util.Optional;

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
        Optional<Election> opt = ctx.svc().getElection(id);
        if (opt.isEmpty()) { ctx.sender().sendMessage("Election not found."); return; }
        String json = ExportElection.buildJson(opt.get(), true, ctx);
        try {
            String url = BytebinClient.uploadJson(json);
            ctx.sender().sendMessage("Exported (admin) to: " + url);
        } catch (Exception ex) {
            ctx.sender().sendMessage("Export failed: " + ex.getMessage());
        }
    }

    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args().length==1) return ctx.filter(ctx.electionIds(), ctx.args()[0]);
        return List.of();
    }
}

