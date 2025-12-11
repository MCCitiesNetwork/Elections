package net.democracycraft.elections.src.command.subcommands;

import net.democracycraft.elections.src.command.framework.CommandContext;
import net.democracycraft.elections.src.command.framework.Subcommand;
import net.democracycraft.elections.src.ui.manager.ElectionListMenu;

import java.util.List;

/**
 * Opens the in-game Elections Manager menu.
 * <p>
 * Usage:
 * - /elections manager
 * <p>
 * Notes:
 * - Must be executed by a player (not from the console).
 */
public class OpenManager implements Subcommand {
    @Override
    public List<String> names() { return List.of("manager", "open"); }

    @Override
    public String permission() { return "elections.manager"; }

    @Override
    public String usage() { return "manager"; }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.isPlayer()) { ctx.sender().sendMessage("This command must be run in-game."); return; }
        var player = ctx.asPlayer();
        var menu = new ElectionListMenu(player, ctx.electionsService());
        menu.open();
    }
}
