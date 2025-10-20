package net.democracycraft.democracyelections.util.permissions;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

import java.util.ArrayList;
import java.util.List;

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
}
