package net.democracycraft.elections.command.subcommands;

import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.util.paste.PasteStorage;

import java.util.List;

/**
 * Paste-related subcommands. Currently supports: delete <id> [confirm].
 */
public class PasteCommand implements Subcommand {
    @Override
    public List<String> names() { return List.of("paste"); }

    @Override
    public String permission() { return "elections.paste"; }

    @Override
    public String usage() { return "paste delete <id> [confirm]"; }

    /**
     * Executes paste operations.
     *
     * Currently supports deleting a paste by id. The actual deletion is performed asynchronously
     * via PasteStorage.deleteAsync and results are reported back to the command sender on the main thread.
     */
    @Override
    public void execute(CommandContext ctx) {
        if (ctx.args().length < 1) { ctx.usage(usage()); return; }
        String action = ctx.args()[0].toLowerCase();
        if (!action.equals("delete")) { ctx.sender().sendMessage("Unknown paste action. Use: delete"); return; }
        if (ctx.args().length < 2) { ctx.sender().sendMessage("Paste id is required"); return; }
        String id = ctx.args()[1];
        if (ctx.args().length < 3 || !"confirm".equalsIgnoreCase(ctx.args()[2])) {
            ctx.sender().sendMessage("This will delete paste '" + id + "'. Run again with 'confirm': /" + ctx.label() + " paste delete " + id + " confirm");
            return;
        }
        PasteStorage ps = ctx.plugin().getPasteStorage();
        // Perform asynchronous deletion and report results on main thread via callbacks
        ps.deleteAsync(id, ok -> {
            ctx.plugin().getLogger().info("[PasteDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", result=" + ok);
            ctx.sender().sendMessage(ok ? "Paste deleted." : "Paste not deleted (unauthorized or not found).");
        }, ex -> {
            ctx.plugin().getLogger().warning("[PasteDelete] actor=" + ctx.sender().getName() + ", id=" + id + ", error=" + ex.getMessage());
            ctx.sender().sendMessage("Delete failed: " + ex.getMessage());
        });
    }

    /**
     * Tab completion for the paste subcommand.
     */
    @Override
    public List<String> complete(CommandContext ctx) {
        if (ctx.args().length == 1) return ctx.filter(List.of("delete"), ctx.args()[0]);
        if (ctx.args().length == 3) return ctx.filter(List.of("confirm"), ctx.args()[2]);
        return List.of();
    }
}
