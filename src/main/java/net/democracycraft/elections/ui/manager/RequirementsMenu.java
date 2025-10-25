package net.democracycraft.elections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.data.RequirementsDto;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.util.permissions.PermissionScanner;
import net.democracycraft.elections.util.permissions.PermissionNodesStore;
import net.democracycraft.elections.util.permissions.data.PermissionNodesDto;
import org.bukkit.permissions.Permission;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import net.democracycraft.elections.ui.common.LoadingMenu;

/**
 * Child dialog to configure election requirements (permissions and active playtime).
 * All texts are configurable via data/menus/RequirementsMenu.yml with placeholders.
 */
public class RequirementsMenu extends ChildMenuImp {

    enum Keys { PLAYTIME, PLAYTIME_TEXT }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public RequirementsMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "requirements_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /**
     * Menu configuration DTO.
     */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Requirements</bold></gold>";
        public String description = "<gray>Toggle permissions required to vote and set minimum active playtime (minutes).</gray>";
        public String playtimeLabel = "<aqua>Active playtime (minutes)</aqua>";
        public String fineAdjustLabel = "<aqua>Fine adjust (minutes)</aqua>";
        public String saveBtn = "<green><bold>Save</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String updatedMsg = "<green><bold>Requirements updated.</bold></green>";
        public String yamlHeader = "RequirementsMenu configuration. Placeholders: %player%, %election_id%.";
        public float playtimeMin = 0f;
        public float playtimeMax = 1_000_000f;
        public float playtimeStep = 1f;
        public Config() {}
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        long currentMinutes = election == null || election.getRequirements() == null ? 0L : election.getRequirements().getMinActivePlaytimeMinutes();
        List<String> currentPerms = election == null || election.getRequirements() == null ? List.of() : election.getRequirements().getPermissions();

        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        Map<String, String> placeholders = Map.of("%election_id%", String.valueOf(electionId));

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.description, placeholders)));

        PermissionNodesStore store = Elections.getInstance().getPermissionNodesStore();
        PermissionNodesDto nodesDto = store.get();
        List<Permission> filteredPermissions = PermissionScanner.getPermissionsForNodesPrefix(nodesDto);
        Map<String, Permission> permissionsByName = new LinkedHashMap<>();
        for (Permission permission : filteredPermissions) permissionsByName.put(permission.getName(), permission);

        for (Permission permission : permissionsByName.values()) {
            String key = permKey(permission.getName());
            boolean initial = currentPerms.contains(permission.getName());
            dialogBuilder.addInput(DialogInput.bool(key, miniMessage("<gray>" + permission.getName() + "</gray>")).initial(initial).build());
        }

        dialogBuilder.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), miniMessage(config.playtimeLabel, placeholders), config.playtimeMin, config.playtimeMax).step(config.playtimeStep).initial((float) currentMinutes).build());
        dialogBuilder.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), miniMessage(config.fineAdjustLabel, placeholders)).labelVisible(true).build());

        dialogBuilder.buttonWithPlayer(miniMessage(config.saveBtn, placeholders), null, Duration.ofMinutes(5), 1, (playerActor, response) -> {
            List<String> newPerms = new ArrayList<>();
            for (Permission permission : permissionsByName.values()) {
                String key = permKey(permission.getName());
                Boolean selection = response.getBoolean(key);
                if (selection != null && selection) newPerms.add(permission.getName());
            }
            Long minutes = null;
            String textInput = response.getText(Keys.PLAYTIME_TEXT.name());
            long boundMin = Math.round(config.playtimeMin);
            long boundMax = Math.round(config.playtimeMax);
            if (textInput != null && !textInput.isBlank()) {
                try {
                    long parsed = Long.parseLong(textInput.trim());
                    if (parsed >= boundMin && parsed <= boundMax) {
                        minutes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minutes == null) {
                Float floatInput = response.getFloat(Keys.PLAYTIME.name());
                long rv = floatInput == null ? currentMinutes : Math.round(floatInput);
                // clamp
                rv = Math.max(boundMin, Math.min(boundMax, rv));
                minutes = Math.max(0, rv);
            }
            final RequirementsDto dto = new RequirementsDto(newPerms, minutes);
            // Offload DB write to async and then update UI back on main thread.
            new LoadingMenu(playerActor, getParentMenu()).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    electionsService.setRequirements(electionId, dto, playerActor.getName());
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            playerActor.sendMessage(miniMessage(config.updatedMsg, placeholders));
                            new ElectionManagerMenu(playerActor, electionsService, electionId).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.backBtn, placeholders), context -> new ElectionManagerMenu(context.player(), electionsService, electionId).open());
        return dialogBuilder.build();
    }

    private String permKey(String permName) {
        return "PERM_" + permName.replace(':', '_').replace('.', '_').replace('-', '_');
    }
}
