package net.democracycraft.elections.internal.command.subcommands;

import net.democracycraft.elections.internal.command.framework.CommandContext;
import net.democracycraft.elections.internal.command.framework.Subcommand;
import net.democracycraft.elections.internal.util.permissions.PermissionScanner;
import net.democracycraft.elections.internal.util.permissions.PermissionNodesStore;
import net.democracycraft.elections.internal.util.permissions.data.PermissionNodesDto;
import org.bukkit.permissions.Permission;

import java.util.*;

/**
 * Reloads the YAML-backed permission nodes store used to filter permission listings.
 */
public class ReloadPermissionNodes implements Subcommand {

    @Override
    public List<String> names() {
        return Arrays.asList("reloadperms", "reloadpermissions", "reloadpermnodes");
    }

    @Override
    public String permission() { return "elections.permissions.reload"; }

    @Override
    public String usage() { return "reloadperms"; }

    @Override
    public void execute(CommandContext ctx) {
        PermissionNodesStore store = ctx.plugin().getPermissionNodesStore();
        store.reload();
        PermissionNodesDto dto = store.get();

        // Compute stats using prefix mode (equals node or startsWith node.)
        List<Permission> all = PermissionScanner.getAllPermissions();
        List<Permission> matched = PermissionScanner.getPermissionsForNodesPrefix(dto);

        Set<String> dtoLower = new LinkedHashSet<>();
        for (String n : dto.nodes()) if (n != null && !n.isBlank()) dtoLower.add(n.trim().toLowerCase(Locale.ROOT));

        // Count which nodes cause most matches within the matched subset
        Map<String, Integer> nodeMatchCounts = new HashMap<>();
        for (Permission p : matched) {
            String ln = p.getName().toLowerCase(Locale.ROOT);
            for (String node : dtoLower) {
                String base = node.endsWith(".") ? node.substring(0, node.length() - 1) : node;
                if (ln.equals(base) || ln.startsWith(base + ".")) nodeMatchCounts.merge(base, 1, Integer::sum);
            }
        }

        // Coverage over ALL permissions: how broad is each configured node?
        Map<String, Integer> nodeCoverageAll = new HashMap<>();
        for (Permission p : all) {
            String ln = p.getName().toLowerCase(Locale.ROOT);
            for (String node : dtoLower) {
                String base = node.endsWith(".") ? node.substring(0, node.length() - 1) : node;
                if (ln.equals(base) || ln.startsWith(base + ".")) nodeCoverageAll.merge(base, 1, Integer::sum);
            }
        }

        // Prepare debug snippets
        List<String> topNodes = nodeMatchCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();

        List<String> sample = matched.stream().limit(10).map(Permission::getName).toList();

        // Log to console
        ctx.plugin().getLogger().info("[PermReload] Mode: prefix matching (equals node or startsWith node.)");
        ctx.plugin().getLogger().info("[PermReload] Loaded nodes: " + dtoLower);
        ctx.plugin().getLogger().info("[PermReload] Registered perms: " + all.size() + ", matched: " + matched.size());
        ctx.plugin().getLogger().info("[PermReload] Top matching nodes (within matches): " + topNodes);

        // Coverage lines per node
        for (String n : dtoLower) {
            String base = n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
            int cov = nodeCoverageAll.getOrDefault(base, 0);
            double pct = all.isEmpty() ? 0D : (100.0 * cov / all.size());
            ctx.plugin().getLogger().info(String.format(Locale.ROOT, "[PermReload] Coverage: node '%s' matches %d/%d (%.1f%%) permissions.", base, cov, all.size(), pct));
            if (pct >= 50.0) {
                ctx.plugin().getLogger().warning(String.format(Locale.ROOT, "[PermReload] Node '%s' is very broad (%.1f%%). Consider using more specific nodes.", base, pct));
            }
        }

        ctx.plugin().getLogger().info("[PermReload] Sample matched perms (10): " + sample);
        if (matched.size() == all.size()) {
            ctx.plugin().getLogger().warning("[PermReload] All permissions matched. Your nodes may be too broad.");
        } else if (matched.isEmpty()) {
            ctx.plugin().getLogger().warning("[PermReload] No permissions matched. Check your nodes config.");
        }

        // Feedback to sender
        ctx.sender().sendMessage("Permission nodes reloaded. Nodes=" + dtoLower.size() + ", matched perms=" + matched.size() + ". See console for details.");
    }
}
