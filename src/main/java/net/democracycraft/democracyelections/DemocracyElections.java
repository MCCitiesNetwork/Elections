package net.democracycraft.democracyelections;

import net.democracycraft.democracyelections.api.service.ElectionsService;
import net.democracycraft.democracyelections.command.ElectionsCommand;
import net.democracycraft.democracyelections.service.MemoryElectionsService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DemocracyElections extends JavaPlugin {

    private static DemocracyElections instance;
    public static DemocracyElections getInstance() { return instance; }

    private ElectionsService electionsService;

    @Override
    public void onEnable() {
        instance = this;
        this.electionsService = new MemoryElectionsService();

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

    public ElectionsService getElectionsService() {
        return electionsService;
    }
}
