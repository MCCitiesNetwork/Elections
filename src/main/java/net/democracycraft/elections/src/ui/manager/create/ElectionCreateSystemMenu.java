package net.democracycraft.elections.src.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.data.VotingSystem;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.AutoDialog;
import org.bukkit.entity.Player;

import java.io.Serializable;

/**
 * Create wizard step: System & Minimum votes. All texts are configurable via per-menu YAML.
 */
public class ElectionCreateSystemMenu extends ChildMenuImp {

    enum Keys { MIN_VOTES, MIN_VOTES_TEXT }

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateSystemMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_system");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    /** Config DTO for this step. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create: System & Minimum</bold></gold>";
        public String currentSystemPrefix = "<gray>Current system: </gray>";
        public String minVotesLabel = "<aqua>Minimum votes</aqua>";
        public String fineAdjustLabel = "<aqua>Fine adjust (min votes)</aqua>";
        public String cycleSystemBtn = "<yellow>Cycle system</yellow>";
        public String nextBtn = "<green><bold>Next</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String yamlHeader = "ElectionCreateSystemMenu configuration.";
        public float minVotesMin = 1f;
        public float minVotesMax = 10000f;
        public float minVotesStep = 1f;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateSystemMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        dialogBuilder.addBody(DialogBody.plainMessage(
                miniMessage(config.currentSystemPrefix, null)
                        .append(miniMessage("<white><bold>" + draft.system.name() + "</bold></white>", null))));
        dialogBuilder.addInput(DialogInput.numberRange(Keys.MIN_VOTES.name(), miniMessage(config.minVotesLabel, null), config.minVotesMin, config.minVotesMax).step(config.minVotesStep).initial((float) draft.minimumVotes).build());
        dialogBuilder.addInput(DialogInput.text(Keys.MIN_VOTES_TEXT.name(), miniMessage(config.fineAdjustLabel, null)).labelVisible(true).build());

        dialogBuilder.button(miniMessage(config.cycleSystemBtn, null), context -> {
            draft.system = (draft.system == VotingSystem.PREFERENTIAL) ? VotingSystem.BLOCK : VotingSystem.PREFERENTIAL;
            new ElectionCreateSystemMenu(context.player(), wizard, draft).open();
        });

        dialogBuilder.buttonWithPlayer(miniMessage(config.nextBtn, null), null, (playerActor, response) -> {
            Integer minVotes = null;
            String textInput = response.getText(Keys.MIN_VOTES_TEXT.name());
            int boundMin = Math.round(config.minVotesMin);
            int boundMax = Math.round(config.minVotesMax);
            if (textInput != null && !textInput.isBlank()) {
                try {
                    int parsed = Integer.parseInt(textInput.trim());
                    if (parsed >= boundMin && parsed <= boundMax) {
                        minVotes = parsed;
                    }
                } catch (NumberFormatException ignored) { }
            }
            if (minVotes == null) {
                Float rangeValue = response.getFloat(Keys.MIN_VOTES.name());
                int rv = rangeValue == null ? draft.minimumVotes : Math.round(rangeValue);
                rv = Math.max(boundMin, Math.min(boundMax, rv));
                minVotes = rv;
            }
            draft.minimumVotes = minVotes;
            new ElectionCreateConfirmMenu(playerActor, wizard, draft).open();
        });

        dialogBuilder.button(miniMessage(config.backBtn, null), context -> new ElectionCreateRequirementsMenu(context.player(), wizard, draft).open());
        return dialogBuilder.build();
    }
}
