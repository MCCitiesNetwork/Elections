package net.democracycraft.elections.src.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.data.RequirementsDto;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.util.permissions.PermissionNodesStore;
import net.democracycraft.elections.src.util.permissions.PermissionScanner;
import net.democracycraft.elections.src.util.permissions.data.PermissionNodesDto;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.io.Serializable;
import java.util.*;

public class ElectionCreateRequirementsMenu extends ChildMenuImp {

    enum Keys { PLAYTIME, PLAYTIME_TEXT }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateRequirementsMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_requirements");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    /** Config DTO for this step. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create: Requirements</bold></gold>";
        public String description = "<gray>Toggle permissions required and set minimum active playtime (minutes).</gray>";
        public String playtimeLabel = "<aqua>Active playtime (minutes)</aqua>";
        public String fineAdjustLabel = "<aqua>Fine adjust (minutes)</aqua>";
        public String nextBtn = "<green><bold>Next</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "ElectionCreateRequirementsMenu configuration.";
        public float playtimeMin = 0f;
        public float playtimeMax = 1_000_000f;
        public float playtimeStep = 1f;
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateRequirementsMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }


    }

    private Dialog build() {
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);

        dialogBuilder.addBody(DialogBody.plainMessage(miniMessage(config.description, null)));

        PermissionNodesStore store = Elections.getInstance().getPermissionNodesStore();
        PermissionNodesDto nodesDto = store.get();
        // Build expanded node strings including hierarchical prefixes
        List<String> expandedNodes = PermissionScanner.buildExpandedNodes(nodesDto);
        LinkedHashSet<String> nodeSet = new LinkedHashSet<>(expandedNodes);
        // Map node -> key
        Map<String, String> nodeToKey = new LinkedHashMap<>();
        for (String node : nodeSet) {
            if (node == null || node.isBlank()) continue;
            String key = permKey(node);
            nodeToKey.put(node, key);
            boolean initial = draft.requirements.permissions().contains(node);
            dialogBuilder.addInput(DialogInput.bool(key, miniMessage("<gray>" + node + "</gray>")).initial(initial).build());
        }

        dialogBuilder.addInput(DialogInput.numberRange(Keys.PLAYTIME.name(), miniMessage(config.playtimeLabel, null), config.playtimeMin, config.playtimeMax)
                .step(config.playtimeStep)
                .initial((float) draft.requirements.minActivePlaytimeMinutes())
                .build());
        dialogBuilder.addInput(DialogInput.text(Keys.PLAYTIME_TEXT.name(), miniMessage(config.fineAdjustLabel, null)).labelVisible(true).build());

        dialogBuilder.buttonWithPlayer(miniMessage(config.nextBtn, null), null, (playerActor, response) -> {
            List<String> newPerms = new ArrayList<>();
            for (Map.Entry<String, String> e : nodeToKey.entrySet()) {
                String node = e.getKey();
                String key = e.getValue();
                Boolean selected = response.getBoolean(key);
                if (selected != null && selected) newPerms.add(node);
            }
            Long minutes = null;
            String text = response.getText(Keys.PLAYTIME_TEXT.name());
            long boundMin = Math.round(config.playtimeMin);
            long boundMax = Math.round(config.playtimeMax);
            if (text != null && !text.isBlank()) {
                try {
                    long parsed = Long.parseLong(text.trim());
                    if (parsed >= boundMin && parsed <= boundMax) {
                        minutes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minutes == null) {
                Float floatInput = response.getFloat(Keys.PLAYTIME.name());
                long rv = floatInput == null ? draft.requirements.minActivePlaytimeMinutes() : Math.round(floatInput);
                rv = Math.max(boundMin, Math.min(boundMax, rv));
                minutes = Math.max(0, rv);
            }
            draft.requirements = new RequirementsDto(newPerms, minutes);
            new ElectionCreateSystemMenu(playerActor, wizard, draft).open();
        });

        dialogBuilder.button(miniMessage(config.backBtn, null), context -> new ElectionCreateDurationMenu(context.player(), wizard, draft).open());
        return dialogBuilder.build();
    }

    private String permKey(String permName) {
        return "PERM_" + permName.replace(':', '_').replace('.', '_').replace('-', '_');
    }
}
