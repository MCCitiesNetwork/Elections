package net.democracycraft.elections.util.permissions;

import net.democracycraft.elections.util.permissions.data.PermissionNodesConfig;
import net.democracycraft.elections.util.permissions.data.PermissionNodesDto;
import net.democracycraft.elections.util.yml.AutoYML;
import net.democracycraft.elections.util.config.DataFolder;

import java.util.Collections;
import java.util.List;

/**
 * Small YAML-backed store for PermissionNodesDto.
 * Uses AutoYML for persistence under the plugin's data folder (permissions/ by default).
 */
public class PermissionNodesStore {

    private final AutoYML<PermissionNodesConfig> yml;
    private volatile PermissionNodesConfig cache;

    /**
     * Builds a store that persists to permissions/permission-nodes.yml with a brief header.
     */
    public PermissionNodesStore() {
        this("permission-nodes", defaultHeader());
    }

    /**
     * Builds a store with a custom file name (without .yml) and custom header.
     *
     * @param fileName base file name (".yml" will be appended if missing)
     * @param header   header comment written at the top of the file
     */
    public PermissionNodesStore(String fileName, String header) {
        this.yml = AutoYML.create(PermissionNodesConfig.class, fileName, DataFolder.PERMISSIONS, header);
        // Eager load to have a ready cache
        reload();
    }

    /**
     * Reloads the configuration from disk, creating a default one if missing.
     */
    public synchronized void reload() {
        PermissionNodesConfig loaded = yml.loadOrCreate(PermissionNodesConfig::new);
        this.cache = loaded == null ? new PermissionNodesConfig() : loaded;
    }

    /**
     * Returns the current DTO view of the permission nodes.
     */
    public PermissionNodesDto get() {
        PermissionNodesConfig c = ensureCache();
        return c.toDto();
    }

    /**
     * Convenience accessor that returns the list of nodes directly.
     */
    public List<String> getNodes() {
        PermissionNodesConfig c = ensureCache();
        List<String> nodes = c.getNodes();
        return nodes == null ? Collections.emptyList() : Collections.unmodifiableList(nodes);
    }

    /**
     * Saves a new DTO to disk and updates the in-memory cache.
     */
    public synchronized void save(PermissionNodesDto dto) {
        PermissionNodesConfig cfg = PermissionNodesConfig.fromDto(dto);
        yml.save(cfg);
        this.cache = cfg;
    }

    private PermissionNodesConfig ensureCache() {
        PermissionNodesConfig local = this.cache;
        if (local == null) {
            synchronized (this) {
                if (cache == null) reload();
                local = cache;
            }
        }
        return local;
    }

    private static String defaultHeader() {
        return String.join("\n",
                "Permission nodes configuration",
                "Each entry should be a permission node string (e.g., 'my.plugin.feature.use').",
                "This file is loaded and saved automatically by the plugin.");
    }
}

