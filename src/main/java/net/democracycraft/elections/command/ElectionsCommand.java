package net.democracycraft.elections.command;

import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.framework.CommandContext;
import net.democracycraft.elections.command.framework.Subcommand;
import net.democracycraft.elections.command.subcommands.OpenManager;
import net.democracycraft.elections.command.subcommands.ReloadPermissionNodes;
import net.democracycraft.elections.command.subcommands.HealthCommand;
import net.democracycraft.elections.command.subcommands.ExportCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Single-entry command with subcommands for UI and exporting.
 */
public class ElectionsCommand implements CommandExecutor, TabCompleter {

    private final Elections plugin;
    private final ElectionsService svc;
    private final Map<String, Subcommand> registry = new LinkedHashMap<>();

    public ElectionsCommand(Elections plugin, ElectionsService svc) {
        this.plugin = plugin;
        this.svc = svc;
        register(new OpenManager());
        register(new ExportCommand());
        register(new ReloadPermissionNodes());
        register(new HealthCommand());
    }

    private void register(Subcommand sub) {
        for (String n : sub.names()) registry.put(n.toLowerCase(Locale.ROOT), sub);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            Subcommand manager = registry.get("manager");
            if (manager == null) return true;
            if (!manager.hasPermission(sender)) { sender.sendMessage("You don't have permission."); return true; }
            try { manager.execute(new CommandContext(plugin, svc, sender, label, args)); } catch (Exception ex) { sender.sendMessage("An error occurred: " + ex.getMessage()); }
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
            // Show only subcommands the sender has permission for; prefer the first alias of each
            Set<Subcommand> uniques = new LinkedHashSet<>(registry.values());
            for (Subcommand sub : uniques) {
                if (sub.hasPermission(sender)) {
                    List<String> names = sub.names();
                    if (!names.isEmpty()) base.add(names.getFirst());
                }
            }
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(prefix)).sorted().toList();
        }
        Subcommand sub = registry.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null || !sub.hasPermission(sender)) return Collections.emptyList();
        return sub.complete(new CommandContext(plugin, svc, sender, alias, args).next());
    }
}
