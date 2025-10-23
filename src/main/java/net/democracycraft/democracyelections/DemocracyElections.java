package net.democracycraft.democracyelections;

import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.command.ElectionsCommand;
import net.democracycraft.democracyelections.service.MemoryElectionsService;
import net.democracycraft.democracyelections.util.listener.PollInteractListener;
import net.democracycraft.democracyelections.util.permissions.PermissionNodesStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point for DemocracyElections.
 *
 * Responsibilities:
 * - Initialize core services (in-memory ElectionsService).
 * - Initialize YAML-backed PermissionNodesStore.
 * - Register the root command executor and tab completer.
 */
public class DemocracyElections extends JavaPlugin {

    private static DemocracyElections instance;
    /**
     * @return the active plugin singleton instance.
     */
    public static DemocracyElections getInstance() { return instance; }

    private ElectionsService electionsService;
    private PermissionNodesStore permissionNodesStore;

    @Override
    public void onEnable() {
        instance = this;
        this.electionsService = new MemoryElectionsService();
        this.permissionNodesStore = new PermissionNodesStore();

        startMainCommand();
        // Register Bukkit listeners (non-dynamic) for voting booth interactions
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PollInteractListener(this, electionsService), this);
    }

    private void startMainCommand() {
        PluginCommand cmd = getCommand("democracyelections");
        if (cmd != null) {
            ElectionsCommand executor = new ElectionsCommand(this, electionsService);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'democracyelections' not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    /**
     * @return the elections application service instance.
     */
    public ElectionsService getElectionsService() {
        return electionsService;
    }

    /**
     * @return the YAML-backed permission nodes store used for UI filtering.
     */
    public PermissionNodesStore getPermissionNodesStore() {
        return permissionNodesStore;
    }
}
