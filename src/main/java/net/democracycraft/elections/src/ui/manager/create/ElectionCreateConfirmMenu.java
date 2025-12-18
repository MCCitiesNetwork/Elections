package net.democracycraft.elections.src.ui.manager.create;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import net.democracycraft.elections.Elections;
import net.democracycraft.elections.api.ui.ParentMenu;
import net.democracycraft.elections.src.ui.ChildMenuImp;
import net.democracycraft.elections.src.ui.manager.ElectionManagerMenu;
import net.democracycraft.elections.api.ui.AutoDialog;
import net.democracycraft.elections.src.ui.common.LoadingMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Serializable;
import java.util.Map;

/**
 * Create wizard step: Confirm and create election. All texts are configurable via per-menu YAML.
 */
public class ElectionCreateConfirmMenu extends ChildMenuImp {

    private final ElectionCreateWizard wizard;
    private final DraftElection draft;

    public ElectionCreateConfirmMenu(Player player, ParentMenu parent, DraftElection draft) {
        super(player, parent, "create_confirm");
        this.wizard = (ElectionCreateWizard) parent;
        this.draft = draft;
        this.setDialog(build());
    }

    /** Config DTO for this step. */
    public static class Config implements Serializable {
        public String title = "<gold><bold>Create: Confirm</bold></gold>";
        public String titleLabel = "<aqua>Title: </aqua>";
        public String systemLabel = "<aqua>System: </aqua>";
        public String minVotesLabel = "<aqua>Minimum votes: </aqua>";
        public String requirementsLabel = "<aqua>Requirements: </aqua>";
        public String requirementsFormat = "<gray>%perms_count% perms, %minutes% min active playtime</gray>";
        public String closesLabel = "<aqua>Closes: </aqua>";
        public String durationNever = "<gray>Never</gray>";
        public String durationFormat = "%days% d %hours% h %minutes% m";
        public String confirmBtn = "<green><bold>Confirm</bold></green>";
        public String backBtn = "<gray>Back</gray>";
        public String cancelBtn = "<red><bold>Cancel</bold></red>";
        public String createdMsg = "<green><bold>Election created.</bold></green>";
        public String yamlHeader = "ElectionCreateConfirmMenu configuration. Placeholders: %title%, %system%, %min_votes%, %perms_count%, %minutes%, %days%, %hours%, %minutes%.";
        /** Loading dialog title and message while creating. */
        public String loadingTitle = "<gold><bold>Creating</bold></gold>";
        public String loadingMessage = "<gray><italic>Creating election…</italic></gray>";
        /** Whether the dialog can be closed with Escape. */
        public boolean canCloseWithEscape = true;
        public Config() {}

        public static void loadConfig() {
            var yml = getOrCreateMenuYml(Config.class, "ElectionCreateConfirmMenu.yml", new Config().yamlHeader);
            yml.loadOrCreate(Config::new);
        }
    }

    private Dialog build() {
        AutoDialog.Builder dialogBuilder = getAutoDialogBuilder();
        var menuYml = getOrCreateMenuYml(Config.class, getMenuConfigFileName(), new Config().yamlHeader);
        Config config = menuYml.loadOrCreate(Config::new);

        dialogBuilder.title(miniMessage(config.title, null));
        dialogBuilder.canCloseWithEscape(config.canCloseWithEscape);
        dialogBuilder.afterAction(DialogBase.DialogAfterAction.CLOSE);

        String durationStr;
        if (draft.durationDays == null && draft.durationTime == null) {
            durationStr = config.durationNever;
        } else {
            int days = draft.durationDays == null ? 0 : draft.durationDays;
            int hours = draft.durationTime == null ? 0 : draft.durationTime.hour();
            int minutes = draft.durationTime == null ? 0 : draft.durationTime.minute();
            durationStr = applyPlaceholders(config.durationFormat, Map.of(
                    "%days%", String.valueOf(days), "%hours%", String.valueOf(hours), "%minutes%", String.valueOf(minutes)));
        }

        String permsCount = String.valueOf(draft.requirements.permissions().size());
        String minutesRequired = String.valueOf(draft.requirements.minActivePlaytimeMinutes());
        String requirementsSummary = applyPlaceholders(config.requirementsFormat, Map.of("%perms_count%", permsCount, "%minutes%", minutesRequired));

        dialogBuilder.addBody(DialogBody.plainMessage(Component.newline()
                .append(miniMessage(config.titleLabel)).append(miniMessage("<white><bold>" + draft.title + "</bold></white>", null))
                .appendNewline().append(miniMessage(config.systemLabel)).append(miniMessage("<gray>" + draft.system.name() + "</gray>", null))
                .appendNewline().append(miniMessage(config.minVotesLabel)).append(miniMessage("<gray>" + draft.minimumVotes + "</gray>", null))
                .appendNewline().append(miniMessage(config.requirementsLabel)).append(miniMessage(requirementsSummary, null))
                .appendNewline().append(miniMessage(config.closesLabel)).append(miniMessage("<gray>" + durationStr + "</gray>", null))
        ));

        dialogBuilder.button(miniMessage(config.confirmBtn), context -> {
            var service = wizard.getService();
            // Run creation on async thread; then UI update back on main
            new LoadingMenu(context.player(), getParentMenu(), miniMessage(config.loadingTitle, null), miniMessage(config.loadingMessage, null)).open();
            new BukkitRunnable() {
                @Override
                public void run() {
                    var created = service.createElection(draft.title, draft.system, draft.minimumVotes, draft.requirements, context.player().getName());
                    if (draft.durationDays != null || draft.durationTime != null) {
                        service.setDuration(created.getId(), draft.durationDays, draft.durationTime, context.player().getName());
                    }
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            context.player().sendMessage(miniMessage(config.createdMsg));
                            new ElectionManagerMenu(context.player(), service, created.getId()).open();
                        }
                    }.runTask(Elections.getInstance());
                }
            }.runTaskAsynchronously(Elections.getInstance());
        });

        dialogBuilder.button(miniMessage(config.backBtn), context -> new ElectionCreateSystemMenu(context.player(), wizard, draft).open());
        dialogBuilder.button(miniMessage(config.cancelBtn), context -> context.player().closeInventory());
        return dialogBuilder.build();
    }
}
