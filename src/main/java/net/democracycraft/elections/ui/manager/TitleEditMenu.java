package net.democracycraft.elections.ui.manager;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.service.ElectionsService;
import net.democracycraft.elections.ui.ChildMenuImp;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.ui.dialog.AutoDialog;
import net.democracycraft.elections.ui.common.LoadingMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Child dialog to edit an election title. All texts are configurable via a per-menu YAML
 * located under data/menus/TitleEditMenu.yml with placeholders like %player%, %election_id%, %election_title%.
 */
public class TitleEditMenu extends ChildMenuImp {

    enum Keys { TITLE }

    private final ElectionsService electionsService;
    private final int electionId;

    /**
     * @param player player opening the dialog
     * @param parent parent menu
     * @param electionsService elections service
     * @param electionId election identifier
     */
    public TitleEditMenu(Player player, ParentMenu parent, ElectionsService electionsService, int electionId) {
        super(player, parent, "title_edit_" + electionId);
        this.electionsService = electionsService;
        this.electionId = electionId;
        this.setDialog(build());
    }

    /**
     * Menu configuration DTO. Public no-arg fields to be serialized by AutoYML.
     */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Edit Title</bold></gold>";
        public String currentPrefix = "<gray>Current:</gray> ";
        public String currentTitleFormat = "<white><bold>%election_title%</bold></white>";
        public String inputLabel = "<aqua>Title</aqua>";
        public String saveBtn = "<green><bold>Save</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String emptyError = "<red><bold>Title cannot be empty.</bold></red>";
        public String updatedMsg = "<green><bold>Title updated.</bold></green>";
        public String yamlHeader = "TitleEditMenu configuration. Placeholders: %player%, %election_id%, %election_title%.";
        /** Loading dialog title and message while saving. */
        public String loadingTitle = "<gold><bold>Saving</bold></gold>";
        public String loadingMessage = "<gray><italic>Saving changes…</italic></gray>";
        public Config() {}
    }

    private Dialog build() {
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        var election = electionsService.getElection(electionId).orElse(null);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%election_id%", String.valueOf(electionId));
        placeholders.put("%election_title%", election == null ? "" : election.getTitle());

        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        dialogBuilder.title(miniMessage(config.title, placeholders));
        dialogBuilder.canCloseWithEscape(true);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);
        if (election != null) {
            Component currentTitle = miniMessage(config.currentPrefix, placeholders)
                    .append(miniMessage(applyPlaceholders(config.currentTitleFormat, placeholders), null));
            dialogBuilder.addBody(DialogBody.plainMessage(currentTitle));
        }
        dialogBuilder.addInput(DialogInput.text(Keys.TITLE.name(), miniMessage(applyPlaceholders(config.inputLabel, placeholders), null)).labelVisible(true).build());
        dialogBuilder.buttonWithPlayer(miniMessage(config.saveBtn, placeholders), null, Duration.ofMinutes(5), 1, (playerActor, response) -> {
            String newTitleText = response.getText(Keys.TITLE.name());
            if (newTitleText == null || newTitleText.isBlank()) {
                playerActor.sendMessage(miniMessage(config.emptyError, placeholders));
                new TitleEditMenu(playerActor, getParentMenu(), electionsService, electionId).open();
                return;
            }
            // Run DB writes off the main thread and then update the UI back on main.
            new LoadingMenu(playerActor, getParentMenu(), miniMessage(config.loadingTitle, placeholders), miniMessage(config.loadingMessage, placeholders)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    electionsService.setTitle(electionId, newTitleText, playerActor.getName());
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
}
