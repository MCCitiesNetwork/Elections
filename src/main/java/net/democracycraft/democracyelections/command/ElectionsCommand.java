package net.democracycraft.democracyelections.command;

import net.democracycraft.democracyelections.DemocracyElections;
import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.command.framework.CommandContext;
import net.democracycraft.democracyelections.command.framework.Subcommand;
import net.democracycraft.democracyelections.command.subcommands.OpenManager;
import net.democracycraft.democracyelections.command.subcommands.ExportElection;
import net.democracycraft.democracyelections.command.subcommands.ExportAllElection;
import net.democracycraft.democracyelections.command.subcommands.ReloadPermissionNodes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Single-entry command with subcommands for dialogs and exporting.
 */
public class ElectionsCommand implements CommandExecutor, TabCompleter {

    private final DemocracyElections plugin;
    private final ElectionsService svc;
    private final Map<String, Subcommand> registry = new LinkedHashMap<>();

    public ElectionsCommand(DemocracyElections plugin, ElectionsService svc) {
        this.plugin = plugin;
        this.svc = svc;
        register(new OpenManager());
        register(new ExportElection());
        register(new ExportAllElection());
        register(new ReloadPermissionNodes());
    }

    private void register(Subcommand sub) {
        for (String n : sub.names()) registry.put(n.toLowerCase(Locale.ROOT), sub);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            Subcommand open = registry.get("open");
            if (open == null) return true;
            if (!open.hasPermission(sender)) { sender.sendMessage("You don't have permission."); return true; }
            try { open.execute(new CommandContext(plugin, svc, sender, label, args)); } catch (Exception ex) { sender.sendMessage("An error occurred: " + ex.getMessage()); }
            return true;
        }
        Subcommand sub = registry.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) { sender.sendMessage("Unknown subcommand."); return true; }
        if (!sub.hasPermission(sender)) { sender.sendMessage("You don't have permission."); return true; }
        try {
            sub.execute(new CommandContext(plugin, svc, sender, label, args).next());
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Usage error: " + ex.getMessage());
            String usage = sub.usage();
            if (usage != null && !usage.isEmpty()) sender.sendMessage("Usage: /" + label + " " + usage);
        } catch (Exception ex) {
            sender.sendMessage("An error occurred: " + ex.getMessage());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> base = new ArrayList<>();
            // export is available to users
            base.add("export");
            // open/exportall for managers/admins
            if (sender.hasPermission("democracyelections.manager") || sender.hasPermission("elections.manager") || sender.hasPermission("elections.admin") || sender.hasPermission("democracyelections.admin")) {
                base.add("open");
                base.add("exportall");
                // reload perms for admins/managers
                base.add("reloadperms");
            }
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(prefix)).sorted().toList();
        }
        Subcommand sub = registry.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sub.hasPermission(sender)) return Collections.emptyList();
        return sub.complete(new CommandContext(plugin, svc, sender, alias, args).next());
    }
}
