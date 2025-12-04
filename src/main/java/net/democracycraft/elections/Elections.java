package net.democracycraft.elections;

import net.democracycraft.elections.api.model.BallotError;
import net.democracycraft.elections.api.model.BallotErrorConfigProvider;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.command.ElectionsCommand;
import net.democracycraft.elections.database.DatabaseSchema;
import net.democracycraft.elections.database.MySQLManager;
import net.democracycraft.elections.service.SqlElectionsService;
import net.democracycraft.elections.util.export.github.GitHubGistClient;
import net.democracycraft.elections.util.listener.PollInteractListener;
import net.democracycraft.elections.util.listener.PlayerJoinHeadCacheListener;
import net.democracycraft.elections.util.permissions.PermissionNodesStore;
import net.democracycraft.elections.util.config.DataFolder;
import net.democracycraft.elections.util.yml.AutoYML;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.democracycraft.elections.util.config.ConfigPaths;
import net.democracycraft.elections.util.export.local.queue.LocalExportedElectionQueue;

/**
 * Main plugin entry point for DemocracyElections.
 */
public class Elections extends JavaPlugin {

    private static Elections instance;
    /**
     * @return the active plugin singleton instance.
     */
    public static Elections getInstance() { return instance; }

    private ElectionsService electionsService;
    private PermissionNodesStore permissionNodesStore;
    private MySQLManager mysql;
    private DatabaseSchema schema;
    private BukkitTask autoCloseTask;
    private BukkitTask deletedPurgeTask;
    private LocalExportedElectionQueue localQueue;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        // Ensure missing keys are populated from defaults when config.yml already exists
        getConfig().options().copyDefaults(true);
        saveConfig();
        // Store globally for BallotError
        BallotErrorConfigProvider.init();

        // MySQL + schema
        this.mysql = new MySQLManager(this);
        this.mysql.setupDatabase();
        // Force a connectivity check and fail fast if unavailable
        try {
            this.mysql.getConnection();
        } catch (Exception ex) {
            getLogger().severe("MySQL is not reachable or credentials are invalid. Plugin will be disabled. Cause: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            this.schema = new DatabaseSchema(mysql);
            this.schema.createAll();
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize database schema. Plugin will be disabled. Cause: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Persistent service backed by SQL with in-memory mirror
        this.electionsService = new SqlElectionsService(this, mysql, schema);
        // Register service for third-party plugins via Bukkit ServicesManager
        getServer().getServicesManager().register(ElectionsService.class, this.electionsService, this, ServicePriority.Highest);

        // Local queue for exports
        this.localQueue = new LocalExportedElectionQueue(this);

        // Schedule periodic auto-close sweep asynchronously (avoid DB on main thread)
        SqlElectionsService sql = (SqlElectionsService) this.electionsService;
        int seconds = getConfig().getInt(ConfigPaths.AUTO_CLOSE_SWEEP_SECONDS.getPath(), 60);
        if (seconds < 1) seconds = 60; // sane fallback
        this.autoCloseTask = getServer().getScheduler().runTaskTimerAsynchronously(this, sql::runAutoCloseSweep, 20L * 30, 20L * seconds);

        // Schedule periodic purge of DELETED elections after retention
        int purgeSeconds = getConfig().getInt(ConfigPaths.DELETED_PURGE_SWEEP_SECONDS.getPath(), 3600);
        int retentionDays = getConfig().getInt(ConfigPaths.DELETED_RETENTION_DAYS.getPath(), 30);
        if (purgeSeconds < 60) purgeSeconds = 3600;
        if (retentionDays < 0) retentionDays = 30;
        final int rd = retentionDays;
        this.deletedPurgeTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> sql.runDeletedPurgeSweep(rd), 20L * 60, 20L * purgeSeconds);

        this.permissionNodesStore = new PermissionNodesStore();

        startMainCommand();

        registerListener(new PollInteractListener(electionsService));
        registerListener(new PlayerJoinHeadCacheListener(electionsService));


        GitHubGistClient.loadConfig();
    }

    private void registerListener(Listener listener){
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void startMainCommand() {
        PluginCommand cmd = getCommand("elections");
        if (cmd != null) {
            ElectionsCommand executor = new ElectionsCommand(this, electionsService);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Command 'elections' not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        // Cancel scheduled task
        if (autoCloseTask != null) {
            autoCloseTask.cancel();
            autoCloseTask = null;
        }
        if (deletedPurgeTask != null) {
            deletedPurgeTask.cancel();
            deletedPurgeTask = null;
        }
        // Unregister provided service
        getServer().getServicesManager().unregisterAll(this);
        // Shutdown async executor in service (if present)
        if (electionsService instanceof SqlElectionsService sql) {
            try { sql.shutdown(); } catch (Exception ignored) {}
        }
        // Disconnect from MySQL
        if (mysql != null) {
            try { mysql.disconnect(); } catch (Exception ignored) {}
        }
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

    /**
     * @return the MySQL manager instance for database operations.
     */
    public MySQLManager getMySQLManager() { return mysql; }

    /**
     * @return the database schema and AutoTable registry.
     */
    public DatabaseSchema getSchema() { return schema; }

    /**
     * @return local export queue singleton.
     */
    public LocalExportedElectionQueue getLocalExportQueue() { return localQueue; }
}
