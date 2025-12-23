package net.democracycraft.elections.internal.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.model.Election;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.internal.data.RequirementsDto;
import net.democracycraft.elections.internal.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.internal.util.permissions.PermissionScanner;
import net.democracycraft.elections.internal.util.permissions.PermissionNodesStore;
import net.democracycraft.elections.internal.util.permissions.data.PermissionNodesDto;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.*;
import net.democracycraft.elections.internal.ui.common.LoadingMenu;
import net.democracycraft.elections.internal.util.time.TimeUnitUtil;

import java.util.concurrent.TimeUnit;

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
        public String description = "<gray>Toggle permissions required to vote and set minimum active playtime.</gray>";
        public String playtimeLabel = "<aqua>Active playtime (%unit%)</aqua>";
        public String fineAdjustLabel = "<aqua>Fine adjust (%unit%)</aqua>";
        public String saveBtn = "<green><bold>Save</bold></green>";
        public String backBtn = "<dark_gray>Back</dark_gray>";
        public String updatedMsg = "<green><bold>Requirements updated.</bold></green>";
        public String yamlHeader = "RequirementsMenu configuration. Placeholders: %player%, %election_id%, %unit%.";
        public String timeUnit = "hours";
        public float playtimeMin = 0f;
        public float playtimeMax = 1_000f;
        public float playtimeStep = 1f;
        /** Loading dialog title and message while saving. */
        public String loadingTitle = "<gold><bold>Saving</bold></gold>";
        public String loadingMessage = "<gray><italic>Applying requirements…</italic></gray>";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(RequirementsMenu.Config.class, "RequirementsMenu.yml", new RequirementsMenu.Config().yamlHeader);
            yml.loadOrCreate(RequirementsMenu.Config::new);
        }
    }

    private Dialog build() {
        Election election = electionsService.getElection(electionId).orElse(null);
        long currentMinutes = election == null || election.getRequirements() == null ? 0L : election.getRequirements().minActivePlaytimeMinutes();
        List<String> currentPerms = election == null || election.getRequirements() == null ? List.of() : election.getRequirements().permissions();

        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        TimeUnit unit = TimeUnitUtil.parseTimeUnit(config.timeUnit);
        String unitName = TimeUnitUtil.getUnitName(unit);
        double factorToMinutes = switch (unit) {
            case DAYS -> 1440.0;
            case HOURS -> 60.0;
            case SECONDS -> 1.0 / 60.0;
            default -> 1.0;
        };

        Map<String, String> placeholders = Map.of(
                "%election_id%", String.valueOf(electionId),
                "%unit%", unitName
        );

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, placeholders));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.description, placeholders)));

        PermissionNodesStore store = Elections.getInstance().getPermissionNodesStore();
        PermissionNodesDto nodesDto = store.get();
        // Build expanded node strings including hierarchical prefixes
        List<String> expandedNodes = PermissionScanner.buildExpandedNodes(nodesDto);
        // Fallback: if nothing matched, show raw configured nodes so users can still select base nodes
        if (expandedNodes.isEmpty() && nodesDto != null && nodesDto.nodes() != null) {
            expandedNodes = new ArrayList<>(nodesDto.nodes());
        }
        // Deduplicate and keep order
        LinkedHashSet<String> nodeSet = new LinkedHashSet<>(expandedNodes);
        // Map node -> key
        Map<String, String> nodeToKey = new LinkedHashMap<>();
        for (String node : nodeSet) {
            if (node == null || node.isBlank()) continue;
            String key = permKey(node);
            nodeToKey.put(node, key);
            boolean initial = currentPerms.contains(node);
            dialogBuilder.addInput(DialogInput.bool(key, miniMessage("<dark_gray>" + node + "</dark_gray>")).initial(initial).build());
        }

        float displayValue = (float) (currentMinutes / factorToMinutes);

        // Fix floating point artifacts
        displayValue = Math.round(displayValue * 1000.0f) / 1000.0f;
        if (Math.abs(displayValue - Math.round(displayValue)) < 0.001) {
            displayValue = Math.round(displayValue);
        }

        dialogBuilder.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), miniMessage(applyPlaceholders(config.playtimeLabel, placeholders), null), config.playtimeMin, config.playtimeMax).step(config.playtimeStep).initial(displayValue).build());
        dialogBuilder.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), miniMessage(applyPlaceholders(config.fineAdjustLabel, placeholders), null)).labelVisible(true).build());

        float finalDisplayValue = displayValue;
        dialogBuilder.buttonWithPlayer(miniMessage(config.saveBtn, placeholders), null, (playerActor, response) -> {
            List<String> newPerms = new ArrayList<>();
            for (Map.Entry<String, String> e : nodeToKey.entrySet()) {
                String node = e.getKey();
                String key = e.getValue();
                Boolean selection = response.getBoolean(key);
                if (selection != null && selection) newPerms.add(node);
            }
            Long minutes = null;
            String textInput = response.getText(Keys.PLAYTIME_TEXT.name());
            // Bounds are in the configured unit
            float boundMin = config.playtimeMin;
            float boundMax = config.playtimeMax;

            if (textInput != null && !textInput.isBlank()) {
                try {
                    float parsed = Float.parseFloat(textInput.trim());
                    if (parsed >= boundMin && parsed <= boundMax) {
                        minutes = Math.round(parsed * factorToMinutes);
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minutes == null) {
                Float floatInput = response.getFloat(Keys.PLAYTIME.name());
                float rv = floatInput == null ? finalDisplayValue : floatInput;
                rv = Math.max(boundMin, Math.min(boundMax, rv));
                minutes = Math.round(rv * factorToMinutes);
            }
            // Ensure non-negative
            minutes = Math.max(0, minutes);

            final RequirementsDto dto = new RequirementsDto(newPerms, minutes);
            // Offload DB write to async and then update UI back on main thread.
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
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
