package net.democracycraft.elections.internal.util.permissions;

import org.bukkit.permissions.Permission;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PermissionPath is a lightweight, immutable view over a permission name.
 * It splits a permission string by '.' into atomic nodes for easier matching.
 * Example: "my.plugin.feature.use" -> ["my", "plugin", "feature", "use"].
 */
public final class PermissionPath {

    private final String name;
    private final List<String> nodes; // original casing preserved
    private final Set<String> nodesLower; // for fast, case-insensitive lookup

    /**
     * Creates a PermissionPath from a raw permission name.
     * Blank segments are ignored; surrounding whitespace is trimmed.
     *
     * @param name permission name (e.g., "my.plugin.feature.use").
     */
    public PermissionPath(String name) {
        this.name = name == null ? "" : name.trim();
        if (this.name.isEmpty()) {
            this.nodes = Collections.emptyList();
            this.nodesLower = Collections.emptySet();
        } else {
            List<String> parts = Arrays.stream(this.name.split("\\."))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            this.nodes = parts;
            this.nodesLower = parts.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /**
     * Creates a PermissionPath from a Bukkit Permission.
     */
    public static PermissionPath from(Permission permission) {
        return new PermissionPath(permission == null ? null : permission.getName());
    }

    /**
     * @return immutable list of atomic nodes (original casing).
     */
    public List<String> getNodes() {
        return nodes;
    }

    /**
     * @return the original permission name.
     */
    public String getName() {
        return name;
    }

    /**
     * Checks if this permission contains at least one node present in the provided set.
     * Comparison is case-insensitive.
     *
     * @param targetNodesLower target nodes in lowercase (recommended: pre-normalized).
     * @return true if any node matches.
     */
    public boolean containsAnyNodeLower(Set<String> targetNodesLower) {
        if (targetNodesLower == null || targetNodesLower.isEmpty() || nodesLower.isEmpty()) return false;
        for (String n : nodesLower) {
            if (targetNodesLower.contains(n)) return true;
        }
        return false;
    }

    /**
     * Returns all hierarchical prefixes for this permission, from the first segment up to full depth.
     * Example: name="group.voter.otherperm" -> ["group", "group.voter", "group.voter.otherperm"].
     * The output preserves original casing.
     */
    public List<String> getPrefixes() {
        if (nodes.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(nodes.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(nodes.get(i));
            out.add(sb.toString());
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public String toString() {
        return name;
    }
}
