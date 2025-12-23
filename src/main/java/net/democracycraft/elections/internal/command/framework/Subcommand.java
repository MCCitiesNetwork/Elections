package net.democracycraft.elections.internal.command.framework;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public interface Subcommand {
    List<String> names();
    String permission(); // null or empty = elections.user
    String usage();

    default boolean hasPermission(CommandSender sender) {
        String p = permission();
        String eff = (p == null || p.isBlank()) ? "elections.user" : p;
        return sender.hasPermission(eff) || sender.hasPermission("elections.admin");
    }

    void execute(CommandContext ctx);

    default List<String> complete(CommandContext ctx) { return Collections.emptyList(); }
}
