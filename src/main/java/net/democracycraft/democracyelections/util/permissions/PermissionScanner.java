package net.democracycraft.democracyelections.util.permissions;

import net.democracycraft.democracyelections.util.permissions.data.PermissionNodesDto;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class that retrieves all registered permission nodes in the server.
 * Works with Bukkit, Paper, and compatible plugin permission registration.
 */
public class PermissionScanner {

    /**
     * Gets all registered permissions on the server.
     *
     * @return A list of permission node strings (e.g., "minecraft.command.gamemode").
     */
    public static List<Permission> getAllPermissions() {
        return new ArrayList<>(Bukkit.getServer().getPluginManager().getPermissions());
    }

    /**
     * Gets all permissions that start with a specific prefix.
     *
     * @param prefix The prefix to filter by (e.g., "minecraft.", "myplugin.").
     * @return A filtered list of permission strings.
     */
    public static List<Permission> getPermissionsStartingWith(String prefix) {
        List<Permission> result = new ArrayList<>();

        for (Permission perm : Bukkit.getServer().getPluginManager().getPermissions()) {
            if (perm.getName().startsWith(prefix)) {
                result.add(perm);
            }
        }

        return result;
    }

    public static List<Permission> getPermissionsContaining(String substring) {
        List<Permission> result = new ArrayList<>();

        for (Permission perm : Bukkit.getServer().getPluginManager().getPermissions()) {
            if (perm.getName().contains(substring)) {
                result.add(perm);
            }
        }

        return result;
    }

    /**
     * Gets permissions that match configured nodes by prefix (case-insensitive).
     * A permission matches if it equals the node or starts with "node.".
     * Examples:
     * - node "elections" matches "elections" and "elections.manager.reload".
     * - node "elections.manager" matches "elections.manager" and "elections.manager.view".
     */
    public static List<Permission> getPermissionsForNodesPrefix(PermissionNodesDto dto) {
        List<Permission> result = new ArrayList<>();
        if (dto == null || dto.nodes() == null || dto.nodes().isEmpty()) return result;

        // Normalize nodes: lowercase, trim, strip trailing '.'
        Set<String> nodes = new LinkedHashSet<>();
        for (String n : dto.nodes()) {
            if (n == null) continue;
            String s = n.trim().toLowerCase(Locale.ROOT);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            if (!s.isEmpty()) nodes.add(s);
        }
        if (nodes.isEmpty()) return result;

        for (Permission perm : Bukkit.getServer().getPluginManager().getPermissions()) {
            String name = perm.getName();
            if (name == null || name.isEmpty()) continue;
            String ln = name.toLowerCase(Locale.ROOT);
            for (String node : nodes) {
                if (ln.equals(node) || ln.startsWith(node + ".")) {
                    result.add(perm);
                    break;
                }
            }
        }
        return result;
    }
}
