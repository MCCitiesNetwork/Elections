package net.democracycraft.democracyelections.command.subcommands;

import net.democracycraft.democracyelections.command.framework.CommandContext;
import net.democracycraft.democracyelections.command.framework.Subcommand;
import net.democracycraft.democracyelections.ui.manager.ElectionListMenu;

import java.util.List;

public class OpenManager implements Subcommand {
    @Override
    public List<String> names() { return List.of("open"); }

    @Override
    public String permission() { return "elections.manager"; }

    @Override
    public String usage() { return "open"; }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.sender.sendMessage("This command must be run in-game."); return; }
        var player = ctx.asPlayer();
        var menu = new ElectionListMenu(player, ctx.svc);
        menu.open();
    }
}
